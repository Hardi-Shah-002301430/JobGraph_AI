package com.jobgraph.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder
public class TrackingResponse {
    private Long id;
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String status;
    private String notes;
    private Instant appliedAt;
    private Instant updatedAt;
    private Double matchScore;
}
