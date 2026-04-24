package com.jobgraph.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobgraph.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the Groq / Mistral OpenAI-compatible chat API.
 * Used by every agent that needs LLM reasoning.
 *
 * Guards against Groq's free-tier rate limits:
 *   1. A semaphore serializes calls so we never have more than MAX_CONCURRENT
 *      requests in flight at once — even when the Akka dispatcher fans out
 *      dozens of ScoreJob messages in parallel.
 *   2. A minimum spacing between successful calls prevents bursts that blow
 *      the per-minute quota.
 *   3. On HTTP 429, we honor the server's `Retry-After` header (or fall back
 *      to exponential backoff) and retry up to MAX_RETRIES times before
 *      giving up.
 *
 * Net effect: the pipeline gets slower under load, but individual LLM calls
 * stop failing with "LLM service unavailable".
 */
@Service
@Slf4j
public class LlmService {

    /** Groq free tier is ~30 RPM. 1 in-flight call + pacing keeps us safe. */
    private static final int MAX_CONCURRENT = 1;

    /** Minimum spacing between consecutive successful calls (ms). */
    private static final long MIN_CALL_INTERVAL_MS = 1200;

    /** How many times we retry a single call on 429 before giving up. */
    private static final int MAX_RETRIES = 4;

    /** Fallback wait when Retry-After header is missing. */
    private static final long DEFAULT_BACKOFF_MS = 2000;

    private final WebClient llmClient;
    private final String model;
    private final ObjectMapper objectMapper;

    private final Semaphore gate = new Semaphore(MAX_CONCURRENT, true);
    private volatile long lastCallAt = 0L;

    public LlmService(AppProperties props, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.model = props.getLlm().getModel();
        this.objectMapper = objectMapper;
        this.llmClient = webClientBuilder
                .baseUrl(props.getLlm().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getLlm().getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Send a chat completion request and return the assistant text.
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, 0.3);
    }

    public String chat(String systemPrompt, String userPrompt, double temperature) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            gate.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM call interrupted while waiting for slot", ie);
        }

        try {
            // Pace requests so we don't blow the per-minute quota even when
            // many agents are calling us back-to-back.
            long sinceLast = System.currentTimeMillis() - lastCallAt;
            if (sinceLast < MIN_CALL_INTERVAL_MS) {
                sleepQuietly(MIN_CALL_INTERVAL_MS - sinceLast);
            }

            return callWithRetry(body);

        } finally {
            lastCallAt = System.currentTimeMillis();
            gate.release();
        }
    }

    private String callWithRetry(Map<String, Object> body) {
        int attempt = 0;
        long backoff = DEFAULT_BACKOFF_MS;

        while (true) {
            attempt++;
            try {
                String response = llmClient.post()
                        .uri("/chat/completions")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = objectMapper.readTree(response);
                return root.path("choices").get(0).path("message").path("content").asText();

            } catch (WebClientResponseException.TooManyRequests e) {
                if (attempt > MAX_RETRIES) {
                    log.error("LLM call failed after {} retries (429)", MAX_RETRIES, e);
                    throw new RuntimeException("LLM service unavailable (rate limited)", e);
                }
                long wait = resolveRetryAfter(e, backoff);
                log.warn("Groq 429 — retry {}/{} after {} ms", attempt, MAX_RETRIES, wait);
                sleepQuietly(wait);
                backoff = Math.min(backoff * 2, 15_000);   // exp. backoff, capped
            } catch (Exception e) {
                log.error("LLM call failed", e);
                throw new RuntimeException("LLM service unavailable", e);
            }
        }
    }

    /** Honor Groq's `Retry-After` header (seconds) when present. */
    private long resolveRetryAfter(WebClientResponseException e, long fallbackMs) {
        try {
            String h = e.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            if (h == null || h.isBlank()) return fallbackMs;
            double seconds = Double.parseDouble(h.trim());
            return Math.max(500L, (long) (seconds * 1000));
        } catch (Exception ignore) {
            return fallbackMs;
        }
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Convenience — call the LLM expecting a JSON response and parse it.
     */
    public JsonNode chatJson(String systemPrompt, String userPrompt) {
        String raw = chat(systemPrompt + "\nRespond ONLY with valid JSON.", userPrompt, 0.1);
        try {
            // strip markdown fences if present
            String cleaned = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.error("Failed to parse LLM JSON: {}", raw, e);
            throw new RuntimeException("LLM returned invalid JSON", e);
        }
    }
}