package com.jobgraph.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "jobgraph")
public class AppProperties {

    private Llm llm = new Llm();
    private Polling polling = new Polling();
    private Slack slack = new Slack();
    private Email email = new Email();
    private Matching matching = new Matching();
    private Adzuna adzuna = new Adzuna();
    private Demo demo = new Demo();

    @Getter @Setter
    public static class Demo {
        /**
         * When true, TrackerAgent skips company-name matching and applies the
         * classification to the most recent open tracking row for the user.
         * Set jobgraph.demo.mode=true in application.yml for presentations.
         */
        private boolean mode = false;
    }

    @Getter @Setter
    public static class Llm {
        private String provider = "groq";
        private String apiKey;
        private String model = "llama3-70b-8192";
        private String baseUrl = "https://api.groq.com/openai/v1";
    }

    @Getter @Setter
    public static class Polling {
        private int intervalMinutes = 1;// testing with 1 min interval; change to 15 or 30 in production
    }

    @Getter @Setter
    public static class Slack {
        private String webhookUrl;
        private String channel = "#job-alerts";
    }

    @Getter @Setter
    public static class Email {
        /** IMAP server hostname, e.g. imap.gmail.com */
        private String imapHost;
        /** IMAPS port — standard is 993. */
        private int imapPort = 993;
        /** Full email address used to sign in. */
        private String username;
        /** App password (NOT account password) for Gmail/Outlook 2FA users. */
        private String password;
        /** INBOX by default; change to a labeled folder like "JobApps". */
        private String folder = "INBOX";
        /** Max emails fetched per poll — guards against one bad run
         *  flooding the LLM when a user has thousands of unread messages. */
        private int fetchBatchSize = 20;
        /** How often the scheduler polls for new mail. Independent of the
         *  job-polling interval so mail can be more responsive than scraping. */
        private int pollIntervalMinutes = 5;
        /** Feature flag — when false, IMAP is entirely skipped and only
         *  the manual-paste endpoint works. */
        private boolean enabled = false;
    }

    @Getter @Setter
    public static class Matching {
        /** Scores at or above this value trigger a Slack alert. */
        private double alertThreshold = 50.0;
    }

    @Getter @Setter
    public static class Adzuna {
        /** From https://developer.adzuna.com — free tier is 250 calls/day. */
        private String appId;
        private String appKey;
    }
}