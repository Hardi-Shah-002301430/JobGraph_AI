package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Tracks which IMAP message IDs we've already processed, so the TrackerAgent's
 * periodic poll doesn't re-classify the same email on every tick.
 *
 * Identified by the RFC 822 Message-ID header — unique per email by standard.
 * On rare cases where a server doesn't provide one, we fall back to
 * folder-name + UID.
 */
@Entity
@Table(name = "seen_emails",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "message_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SeenEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** RFC 822 Message-ID header, or "{folder}:{uid}" fallback. Up to 500 chars. */
    @Column(name = "message_id", nullable = false, length = 500)
    private String messageId;

    @Column(name = "seen_at", nullable = false)
    @Builder.Default
    private Instant seenAt = Instant.now();
}