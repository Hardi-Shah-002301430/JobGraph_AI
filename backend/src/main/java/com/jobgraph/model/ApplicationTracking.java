package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "application_tracking", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "resume_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeProfile resume;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "application_status")
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.BOOKMARKED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}