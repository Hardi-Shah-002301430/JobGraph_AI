package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "job_alerts", uniqueConstraints =
        @UniqueConstraint(columnNames = {"user_id", "job_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "top_score", nullable = false)
    private Double topScore;

    @Column(name = "resume_count", nullable = false)
    private Integer resumeCount;

    @Column(name = "sent_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant sentAt = Instant.now();
}