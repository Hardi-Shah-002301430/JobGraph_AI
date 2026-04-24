package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "match_scores", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "resume_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeProfile resume;

    @Column(name = "overall_score", nullable = false)
    private Double overallScore;

    @Column(name = "skill_score")
    private Double skillScore;

    @Column(name = "experience_score")
    private Double experienceScore;

    @Column(name = "education_score")
    private Double educationScore;

    @Column(name = "industry_score")
    private Double industryScore;

    @Column(name = "location_score")
    private Double locationScore;

    @Column(name = "skill_detail", columnDefinition = "TEXT")
    private String skillDetail;

    @Column(name = "experience_detail", columnDefinition = "TEXT")
    private String experienceDetail;

    @Column(name = "education_detail", columnDefinition = "TEXT")
    private String educationDetail;

    @Column(name = "industry_detail", columnDefinition = "TEXT")
    private String industryDetail;

    @Column(name = "location_detail", columnDefinition = "TEXT")
    private String locationDetail;

    @Column(name = "resume_tips", columnDefinition = "TEXT")
    private String resumeTips;

    @Column(name = "computed_at", nullable = false)
    @Builder.Default
    private Instant computedAt = Instant.now();
}
