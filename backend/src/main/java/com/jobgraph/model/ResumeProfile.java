package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "resume_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResumeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(length = 200)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skills", columnDefinition = "text[]")
    private List<String> skills;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "education_level", length = 50)
    private String educationLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_roles", columnDefinition = "text[]")
    private List<String> preferredRoles;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /** Human-friendly label for the UI dropdown. */
    @Transient
    public String getDisplayLabel() {
        if (fullName == null || fullName.isBlank()) return "Resume #" + id;
        if (preferredRoles != null && !preferredRoles.isEmpty()) {
            return fullName + " — " + preferredRoles.get(0);
        }
        return fullName;
    }
}