package com.jobgraph.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data @Builder
public class ResumeProfileResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String summary;
    private List<String> skills;
    private Integer experienceYears;
    private String educationLevel;
    private List<String> preferredRoles;
    private Instant createdAt;
}
