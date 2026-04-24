package com.jobgraph.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobgraph.config.AppProperties;
import com.jobgraph.model.Job;

import lombok.extern.slf4j.Slf4j;

/**
 * Adapter for the Adzuna public job-search API.
 *   Docs: https://developer.adzuna.com/overview
 *   Endpoint: GET https://api.adzuna.com/v1/api/jobs/us/search/{page}
 *
 * Keyword-based (unlike per-company ATS adapters): scheduler passes a role
 * like "Platform Engineer" as the keyword, we return US jobs posted in the
 * last 24 hours. Each result's `description` from Adzuna is truncated at
 * ~500 chars, so we follow `redirect_url` and scrape the full JD with jsoup.
 *
 * Signature-compatible with {@link JobBoardAdapter} so PollerAgent can use
 * the existing adapter map: first arg is the keyword (not a URL), second
 * arg is unused (null is fine).
 */
@Service
@Slf4j
public class AdzunaAdapter implements JobBoardAdapter {

    private static final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";
    private static final String COUNTRY = "us";
    private static final int MAX_DAYS_OLD = 1;
    private static final int RESULTS_PER_PAGE = 1;             // testing: keep LLM fan-out tiny to avoid Groq 429s
    private static final int MAX_PAGES = 1;                    // 5 jobs max per poll per keyword
    private static final int SCRAPE_TIMEOUT_MS = 8000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /** Selectors commonly used by career sites, tried in priority order. */
    private static final String[] DESCRIPTION_SELECTORS = {
            "div[data-automation-id=jobPostingDescription]",   // WorkDay
            "div#content",                                      // Greenhouse
            "div.posting-description",                          // Lever
            "div[class*=job-description]",
            "div[class*=jobDescription]",
            "section.job-description",
            "article",
            "main"
    };

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String appKey;

    public AdzunaAdapter(WebClient.Builder webClientBuilder,
                         ObjectMapper objectMapper,
                         AppProperties props) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.appId = props.getAdzuna().getAppId();
        this.appKey = props.getAdzuna().getAppKey();
    }

    @Override
    public String boardType() {
        return "ADZUNA";
    }

    @Override
    public List<Job> fetchJobs(String keyword, Long unused) {
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            log.warn("Adzuna credentials not configured; skipping keyword '{}'", keyword);
            return List.of();
        }
        if (keyword == null || keyword.isBlank()) return List.of();

        List<Job> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<Job> pageJobs = fetchPage(keyword, page);
            all.addAll(pageJobs);
            if (pageJobs.size() < RESULTS_PER_PAGE) break;
        }

        int enriched = 0;
        for (Job job : all) {
            String full = scrapeDescription(job.getApplyUrl());
            if (full != null) {
                job.setDescription(full);
                enriched++;
            }
        }

        log.info("Adzuna '{}': {} jobs fetched, {} enriched with full JD",
                keyword, all.size(), enriched);
        return all;
    }

    // ───────────────────────── Adzuna API call ─────────────────────────

    private List<Job> fetchPage(String keyword, int page) {
        String url = UriComponentsBuilder
                .fromHttpUrl(BASE_URL + "/" + COUNTRY + "/search/" + page)
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("what", keyword)
                .queryParam("results_per_page", RESULTS_PER_PAGE)
                .queryParam("max_days_old", MAX_DAYS_OLD)
                .queryParam("sort_by", "date")
                .queryParam("content-type", "application/json")
                .build()
                .toUriString();

        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseJobs(json);
        } catch (Exception e) {
            log.error("Adzuna fetch failed for keyword='{}' page={}: {}",
                    keyword, page, e.getMessage());
            return List.of();
        }
    }

    private List<Job> parseJobs(String json) {
        List<Job> jobs = new ArrayList<>();
        if (json == null || json.isBlank()) return jobs;

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode results = root.path("results");
            if (!results.isArray()) return jobs;

            for (JsonNode node : results) {
                String externalId = node.path("id").asText("");
                if (externalId.isBlank()) continue;

                Job job = Job.builder()
                        .externalId(externalId)
                        // company is a @ManyToOne — PollerAgent resolves & sets it
                        .title(node.path("title").asText(""))
                        .location(node.path("location").path("display_name").asText("Unspecified"))
                        .department(node.path("company").path("display_name").asText(null))
                        .description(node.path("description").asText(""))
                        .employmentType(mapContractType(node.path("contract_time").asText(null)))
                        .applyUrl(node.path("redirect_url").asText(null))
                        .postedAt(parseInstant(node.path("created").asText(null)))
                        .scrapedAt(Instant.now())
                        .active(true)
                        .build();

                jobs.add(job);
            }
        } catch (Exception e) {
            log.error("Failed to parse Adzuna response", e);
        }
        return jobs;
    }

    // ───────────────────────── Redirect scrape ─────────────────────────

    private String scrapeDescription(String url) {
        if (url == null || url.isBlank()) return null;

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(SCRAPE_TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .get();

            doc.select("script, style, nav, header, footer, svg").remove();

            for (String selector : DESCRIPTION_SELECTORS) {
                Elements matches = doc.select(selector);
                if (!matches.isEmpty()) {
                    String text = matches.first().text().replaceAll("\\s+", " ").trim();
                    if (text.length() > 200) return truncate(text);
                }
            }

            Element largest = null;
            int maxLen = 0;
            for (Element el : doc.select("div, section, article")) {
                int len = el.text().length();
                if (len > maxLen) { maxLen = len; largest = el; }
            }
            if (largest != null && maxLen > 200) {
                return truncate(largest.text().replaceAll("\\s+", " ").trim());
            }
            return null;

        } catch (Exception e) {
            log.debug("Scrape failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String truncate(String s) {
        int max = 30_000;
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ───────────────────────── Helpers ─────────────────────────

    private String mapContractType(String raw) {
        if (raw == null) return "FULL_TIME";
        return switch (raw) {
            case "full_time" -> "FULL_TIME";
            case "part_time" -> "PART_TIME";
            default          -> raw.toUpperCase();
        };
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); }
        catch (DateTimeParseException e) { return null; }
    }
}