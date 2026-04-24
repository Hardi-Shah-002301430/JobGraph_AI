package com.jobgraph.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "careers_url", length = 500)
    private String careersUrl;

    @Column(name = "board_type", nullable = false, length = 50)
    @Builder.Default
    private String boardType = "ASHBY";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 200)
    private String industry;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
