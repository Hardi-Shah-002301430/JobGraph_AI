package com.jobgraph.agent;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobgraph.config.AppProperties;
import com.jobgraph.message.TrackingMessages.*;
import com.jobgraph.model.ApplicationStatus;
import com.jobgraph.model.ApplicationTracking;
import com.jobgraph.model.EmailClassification;
import com.jobgraph.repository.ApplicationTrackingRepository;
import com.jobgraph.repository.EmailClassificationRepository;
import com.jobgraph.service.EmailService;
import com.jobgraph.service.EmailService.EmailSummary;
import com.jobgraph.service.LlmService;
import com.jobgraph.websocket.JobUpdateHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

@Slf4j
public class TrackerAgent extends AbstractBehavior<TrackerCommand> {

    private static final double AUTO_UPDATE_CONFIDENCE = 0.70;
    private static final long DEFAULT_USER_ID = 1L;

    private final LlmService llmService;
    private final EmailService emailService;
    private final ApplicationTrackingRepository trackingRepo;
    private final EmailClassificationRepository classificationRepo;
    private final JobUpdateHandler jobUpdateHandler;
    private final boolean demoMode;

    private static final String SYSTEM_PROMPT = """
        ROLE
        You classify emails related to a user's job applications. Given an
        email's subject line, sender, and body snippet, determine what kind
        of email it is and which company sent it.

        CLASSIFICATION LABELS
        REJECTION         — Explicit rejection. "We've decided to move forward
                            with other candidates", "we won't be proceeding".
        INTERVIEW_INVITE  — Invitation to an interview / phone screen /
                            technical round. "Would you be available for a
                            call", "next step is a technical interview".
        OFFER             — A formal or informal offer of employment.
        FOLLOW_UP         — Neutral update. "We received your application",
                            "reviewing applications this week", scheduling
                            logistics that aren't an interview invite.
        GENERIC           — Automated "thanks for applying" confirmation.
        UNRELATED         — Not about a job application at all (newsletter,
                            marketing, personal).

        COMPANY NAME
        Extract the company name from the sender domain, signature, or body.
        If unclear, return null — never guess.

        CONFIDENCE
        Return how certain you are about the classification, 0.0 to 1.0.

        OUTPUT
        Respond ONLY with JSON, no prose, no markdown:
        {
          "classification": "REJECTION|INTERVIEW_INVITE|OFFER|FOLLOW_UP|GENERIC|UNRELATED",
          "companyName": "string or null",
          "confidence": number (0.0 - 1.0),
          "reasoning": "one short sentence explaining the label"
        }
        """;

    private TrackerAgent(ActorContext<TrackerCommand> ctx,
                         LlmService llmService,
                         EmailService emailService,
                         ApplicationTrackingRepository trackingRepo,
                         EmailClassificationRepository classificationRepo,
                         JobUpdateHandler jobUpdateHandler,
                         boolean demoMode) {
        super(ctx);
        this.llmService = llmService;
        this.emailService = emailService;
        this.trackingRepo = trackingRepo;
        this.classificationRepo = classificationRepo;
        this.jobUpdateHandler = jobUpdateHandler;
        this.demoMode = demoMode;
        log.info("TrackerAgent started (demoMode={})", demoMode);
    }

    public static Behavior<TrackerCommand> create(LlmService llm,
                                                   EmailService emailService,
                                                   ApplicationTrackingRepository trackingRepo,
                                                   EmailClassificationRepository classificationRepo,
                                                   JobUpdateHandler jobUpdateHandler,
                                                   AppProperties appProperties) {
        return Behaviors.setup(ctx ->
                new TrackerAgent(ctx, llm, emailService, trackingRepo,
                        classificationRepo, jobUpdateHandler,
                        appProperties.getDemo().isMode()));
    }

    @Override
    public Receive<TrackerCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(PollEmails.class, this::onPollEmails)
                .onMessage(ClassifyEmail.class, this::onClassify)
                .build();
    }

    private Behavior<TrackerCommand> onPollEmails(PollEmails cmd) {
        try {
            List<EmailSummary> fresh = emailService.fetchUnread(cmd.getUserId());
            if (fresh.isEmpty()) {
                log.debug("Email poll for user={}: no new mail", cmd.getUserId());
                return this;
            }
            log.info("Email poll for user={}: {} new messages to classify",
                    cmd.getUserId(), fresh.size());
            for (EmailSummary email : fresh) {
                getContext().getSelf().tell(new ClassifyEmail(
                        email.getSubject(),
                        email.getSender(),
                        email.getBodySnippet(),
                        getContext().getSystem().ignoreRef()));
            }
        } catch (Exception e) {
            log.error("Email poll failed for user={}", cmd.getUserId(), e);
        }
        return this;
    }

    private Behavior<TrackerCommand> onClassify(ClassifyEmail cmd) {
        try {
            String userPrompt = String.format(
                    "SUBJECT: %s%nFROM: %s%nBODY SNIPPET:%n%s",
                    nvl(cmd.getSubject()),
                    nvl(cmd.getSender()),
                    nvl(cmd.getBodySnippet()));

            JsonNode result = llmService.chatJson(SYSTEM_PROMPT, userPrompt);

            String classification = result.path("classification").asText("UNRELATED");
            String companyName    = result.path("companyName").asText(null);
            double confidence     = result.path("confidence").asDouble(0.5);
            String reasoning      = result.path("reasoning").asText("");

            log.info("Email classified: {} (conf={}) company='{}' — {}",
                    classification, confidence, companyName, reasoning);

            // In demo mode: skip company matching, use the most recent open
            // tracking row so the status update always fires for the demo.
            ApplicationTracking matched = demoMode
                    ? findMostRecentOpenTracking(DEFAULT_USER_ID)
                    : findMatchingTracking(DEFAULT_USER_ID, companyName);

            if (matched != null
                    && confidence >= AUTO_UPDATE_CONFIDENCE
                    && !classification.equals("UNRELATED")
                    && !classification.equals("GENERIC")) {

                ApplicationStatus newStatus = mapLabelToStatus(classification, matched.getStatus());
                if (newStatus != null && newStatus != matched.getStatus()) {
                    ApplicationStatus oldStatus = matched.getStatus();
                    matched.setStatus(newStatus);
                    matched.setUpdatedAt(Instant.now());
                    matched.setNotes(appendAutoNote(matched.getNotes(), classification, reasoning));
                    trackingRepo.save(matched);
                    log.info("Tracking {} auto-updated: {} -> {} ({})",
                            matched.getId(), oldStatus, newStatus, classification);
                    jobUpdateHandler.broadcastStatusChange(matched.getId(), newStatus.name());
                }
            }

            classificationRepo.save(EmailClassification.builder()
                    .tracking(matched)
                    .subject(truncate(cmd.getSubject(), 500))
                    .sender(truncate(cmd.getSender(), 300))
                    .bodySnippet(truncate(cmd.getBodySnippet(), 2000))
                    .classification(classification)
                    .confidence(confidence)
                    .build());

            cmd.getReplyTo().tell(new EmailClassified(
                    classification,
                    confidence,
                    matched != null ? matched.getId() : null));

        } catch (Exception e) {
            log.error("Email classification failed", e);
            cmd.getReplyTo().tell(new EmailClassified("ERROR", 0, null));
        }
        return this;
    }

    /** Demo mode: return the most recently updated open tracking row. */
    private ApplicationTracking findMostRecentOpenTracking(long userId) {
        List<ApplicationTracking> rows =
                trackingRepo.findByUserIdOrderByUpdatedAtDesc(userId);
        return rows.stream()
                .filter(t -> t.getStatus() != ApplicationStatus.REJECTED
                          && t.getStatus() != ApplicationStatus.ACCEPTED
                          && t.getStatus() != ApplicationStatus.WITHDRAWN)
                .findFirst()
                .orElse(null);
    }

    /** Production mode: match by company name. */
    private ApplicationTracking findMatchingTracking(long userId, String companyName) {
        if (companyName == null || companyName.isBlank()) return null;
        String cleaned = companyName
                .replaceAll("(?i)\\b(inc|llc|ltd|corp|corporation|gmbh|co)\\.?\\b", "")
                .trim();
        if (cleaned.length() < 3) return null;
        List<ApplicationTracking> candidates =
                trackingRepo.findOpenByUserAndCompany(userId, cleaned);
        if (candidates.isEmpty()) {
            log.info("No open tracking row for user={} company='{}'", userId, cleaned);
            return null;
        }
        if (candidates.size() > 1) {
            log.warn("Multiple open applications at company='{}' — using most recent", cleaned);
        }
        return candidates.get(0);
    }

    private static ApplicationStatus mapLabelToStatus(String label, ApplicationStatus current) {
        return switch (label) {
            case "REJECTION" -> ApplicationStatus.REJECTED;
            case "OFFER"     -> ApplicationStatus.OFFER;
            case "INTERVIEW_INVITE" -> {
                if (current == ApplicationStatus.INTERVIEW
                        || current == ApplicationStatus.OFFER
                        || current == ApplicationStatus.ACCEPTED) yield null;
                yield current == ApplicationStatus.PHONE_SCREEN
                        ? ApplicationStatus.INTERVIEW
                        : ApplicationStatus.PHONE_SCREEN;
            }
            default -> null;
        };
    }

    private static String appendAutoNote(String existing, String label, String reasoning) {
        String auto = String.format("[Auto %s] %s — %s",
                Instant.now().toString().substring(0, 10), label,
                reasoning == null || reasoning.isBlank() ? "classified by tracker" : reasoning);
        return (existing == null || existing.isBlank()) ? auto : existing + "\n" + auto;
    }

    private static String nvl(String s)              { return s == null ? "" : s; }
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}