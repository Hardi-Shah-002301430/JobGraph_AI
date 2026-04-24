package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "email_classifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracking_id")
    private ApplicationTracking tracking;

    @Column(length = 500)
    private String subject;

    @Column(length = 300)
    private String sender;

    @Column(name = "body_snippet", columnDefinition = "TEXT")
    private String bodySnippet;

    @Column(length = 50)
    private String classification;

    private Double confidence;

    @Column(name = "classified_at", nullable = false)
    @Builder.Default
    private Instant classifiedAt = Instant.now();
}
