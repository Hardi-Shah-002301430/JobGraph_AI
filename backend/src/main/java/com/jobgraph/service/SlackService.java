package com.jobgraph.service;

import com.jobgraph.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@Slf4j
public class SlackService {

    private final String webhookUrl;
    private final WebClient webClient;

    public SlackService(AppProperties props, WebClient.Builder webClientBuilder) {
        this.webhookUrl = props.getSlack().getWebhookUrl();
        this.webClient = webClientBuilder.build();
    }

    /**
     * Post a message to the configured Slack webhook.
     *
     * @return true if the message was accepted (HTTP 200)
     */
    public boolean send(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack webhook URL not configured — skipping notification");
            return false;
        }

        try {
            String response = webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", text))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            boolean ok = "ok".equals(response);
            if (!ok) log.warn("Slack webhook returned: {}", response);
            return ok;
        } catch (Exception e) {
            log.error("Failed to send Slack message", e);
            return false;
        }
    }

    /** Convenience: formatted match alert. */
    public boolean sendMatchAlert(String jobTitle, String company, double score) {
        String msg = String.format(
                ":dart: *New Match!* — *%.0f%%* for *%s* at *%s*",
                score, jobTitle, company);
        return send(msg);
    }

    /** Convenience: status change alert. */
    public boolean sendStatusAlert(String jobTitle, String company, String oldStatus, String newStatus) {
        String msg = String.format(
                ":arrows_counterclockwise: *%s* at *%s* — %s → *%s*",
                jobTitle, company, oldStatus, newStatus);
        return send(msg);
    }
}
