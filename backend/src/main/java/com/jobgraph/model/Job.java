package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"external_id", "company_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String location;

    @Column(length = 200)
    private String department;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "employment_type", length = 50)
    private String employmentType;

    @Column(name = "experience_min")
    private Integer experienceMin;

    @Column(name = "experience_max")
    private Integer experienceMax;

    @Column(name = "posted_at")
    private Instant postedAt;

    /** Clickable URL for the user / Slack alert. For Adzuna this is the
     *  redirect_url that lands on the employer's real job page. */
    @Column(name = "apply_url", length = 1000)
    private String applyUrl;

    @Column(name = "scraped_at", nullable = false)
    @Builder.Default
    private Instant scrapedAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}