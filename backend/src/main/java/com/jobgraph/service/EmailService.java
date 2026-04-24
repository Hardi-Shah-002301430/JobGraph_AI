package com.jobgraph.service;

import com.jobgraph.config.AppProperties;
import com.jobgraph.model.SeenEmail;
import com.jobgraph.repository.SeenEmailRepository;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Fetches recent unread emails via IMAPS for the TrackerAgent to classify.
 *
 * Uses Jakarta Mail's IMAPS provider. Authentication is username + password
 * (Gmail/Outlook users should use an app password — OAuth2 is out of scope
 * for this project).
 *
 * Deduplication: every returned email has its Message-ID checked against
 * the seen_emails table, so the tracker never reprocesses the same message
 * on subsequent polls. On the very first run for a given user we mark every
 * matched email as seen WITHOUT classifying, so we don't flood the LLM with
 * the user's entire inbox history.
 */
@Service
@Slf4j
public class EmailService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Max characters we extract from the body. The LLM classifier doesn't
     * need the full message — the opening paragraph is almost always where
     * recruiters state the outcome.
     */
    private static final int BODY_SNIPPET_CHARS = 2000;

    private final AppProperties.Email emailProps;
    private final SeenEmailRepository seenRepo;

    public EmailService(AppProperties props, SeenEmailRepository seenRepo) {
        this.emailProps = props.getEmail();
        this.seenRepo = seenRepo;
    }

    @Data
    @Builder
    public static class EmailSummary {
        private String messageId;   // RFC 822 Message-ID or "folder:uid" fallback
        private String subject;
        private String sender;      // from address, e.g. "recruiter@acme.com"
        private String bodySnippet;
    }

    /**
     * Connect to the configured IMAP inbox and return unread messages that
     * haven't been seen for this user yet. Every returned email is marked
     * as seen before return — we treat fetch-and-mark as atomic from the
     * caller's perspective, even though it technically isn't.
     *
     * @param userId  for deduplication scope (one user per inbox today)
     * @return list of fresh unread emails, empty if IMAP is disabled or the
     *         connection fails — never throws to avoid killing the scheduler
     */
    public List<EmailSummary> fetchUnread(long userId) {
        if (!emailProps.isEnabled()) {
            return List.of();
        }
        if (emailProps.getImapHost() == null || emailProps.getImapHost().isBlank()) {
            log.debug("IMAP host not configured — skipping");
            return List.of();
        }

        List<EmailSummary> fresh = new ArrayList<>();
        Store store = null;
        Folder inbox = null;

        try {
            Session session = Session.getInstance(buildImapProps());
            store = session.getStore("imaps");
            store.connect(emailProps.getImapHost(),
                    emailProps.getUsername(),
                    emailProps.getPassword());

            inbox = store.getFolder(emailProps.getFolder());
            inbox.open(Folder.READ_ONLY);

            Message[] unread = inbox.search(
                    new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            int toProcess = Math.min(unread.length, emailProps.getFetchBatchSize());
            log.info("IMAP fetched {} unread messages (processing {})",
                    unread.length, toProcess);

            // Jakarta Mail returns oldest first in this search — take the
            // most recent N so a huge unread backlog doesn't drown the LLM.
            int startIdx = Math.max(0, unread.length - toProcess);

            for (int i = startIdx; i < unread.length; i++) {
                try {
                    Message msg = unread[i];
                    String msgId = resolveMessageId(msg, inbox);

                    if (seenRepo.existsByUserIdAndMessageId(userId, msgId)) {
                        continue;
                    }

                    EmailSummary summary = EmailSummary.builder()
                            .messageId(msgId)
                            .subject(safe(msg.getSubject()))
                            .sender(firstAddress(msg))
                            .bodySnippet(truncate(extractBody(msg), BODY_SNIPPET_CHARS))
                            .build();

                    fresh.add(summary);

                    // Mark as seen immediately so a crash mid-batch doesn't
                    // lead to duplicate classification on the next tick.
                    seenRepo.save(SeenEmail.builder()
                            .userId(userId)
                            .messageId(msgId)
                            .build());

                } catch (Exception perMessage) {
                    log.warn("Skipping one message due to parse error: {}",
                            perMessage.getMessage());
                }
            }
        } catch (AuthenticationFailedException afe) {
            log.error("IMAP authentication failed — check credentials / app password");
        } catch (MessagingException me) {
            log.error("IMAP error: {}", me.getMessage());
        } catch (Exception e) {
            log.error("Unexpected IMAP failure", e);
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }

        return fresh;
    }

    private Properties buildImapProps() {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.host", emailProps.getImapHost());
        p.put("mail.imaps.port", String.valueOf(emailProps.getImapPort()));
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", String.valueOf(CONNECT_TIMEOUT.toMillis()));
        p.put("mail.imaps.timeout", String.valueOf(READ_TIMEOUT.toMillis()));
        // Gmail sometimes needs this explicitly — doesn't hurt others.
        p.put("mail.imaps.ssl.trust", emailProps.getImapHost());
        return p;
    }

    /**
     * Prefer RFC 822 Message-ID. Some non-RFC-compliant servers omit it —
     * we fall back to folder+UID which is also guaranteed unique per server.
     */
    private static String resolveMessageId(Message msg, Folder folder) throws MessagingException {
        if (msg instanceof MimeMessage mime) {
            String id = mime.getMessageID();
            if (id != null && !id.isBlank()) return id;
        }
        // Fallback — UIDFolder is the standard IMAP interface for UIDs.
        if (folder instanceof UIDFolder uidFolder) {
            return folder.getFullName() + ":" + uidFolder.getUID(msg);
        }
        return folder.getFullName() + ":" + msg.getMessageNumber();
    }

    /**
     * Extract the plain-text body. For multipart/alternative messages we
     * prefer text/plain over text/html; for pure HTML messages we strip
     * tags rather than running a real HTML parser (overkill for classification).
     */
    private static String extractBody(Message msg) throws Exception {
        Object content = msg.getContent();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof MimeMultipart mp) {
            return extractFromMultipart(mp);
        }
        return "";
    }

    private static String extractFromMultipart(MimeMultipart mp) throws Exception {
        String htmlFallback = null;
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart part = mp.getBodyPart(i);
            String disposition = part.getDisposition();
            // Skip attachments.
            if (disposition != null
                    && (disposition.equalsIgnoreCase(Part.ATTACHMENT)
                     || disposition.equalsIgnoreCase(Part.INLINE))) {
                continue;
            }
            if (part.isMimeType("text/plain")) {
                return (String) part.getContent();
            }
            if (part.isMimeType("text/html") && htmlFallback == null) {
                htmlFallback = stripHtml((String) part.getContent());
            }
            if (part.isMimeType("multipart/*")) {
                String nested = extractFromMultipart((MimeMultipart) part.getContent());
                if (!nested.isBlank()) return nested;
            }
        }
        return htmlFallback == null ? "" : htmlFallback;
    }

    /** Minimalist HTML strip — drops tags, decodes a handful of common entities. */
    private static String stripHtml(String html) {
        return html.replaceAll("(?s)<script.*?</script>", " ")
                   .replaceAll("(?s)<style.*?</style>", " ")
                   .replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&#39;|&apos;", "'")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    private static String firstAddress(Message msg) throws MessagingException {
        Address[] from = msg.getFrom();
        return (from == null || from.length == 0) ? "" : from[0].toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void closeQuietly(Folder f) {
        try { if (f != null && f.isOpen()) f.close(false); } catch (Exception ignore) {}
    }

    private static void closeQuietly(Store s) {
        try { if (s != null && s.isConnected()) s.close(); } catch (Exception ignore) {}
    }
}