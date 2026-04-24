package com.jobgraph.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data @Builder
public class DashboardStatsResponse {
    private long totalJobs;
    private long totalCompanies;
    private long totalMatches;
    private long totalApplications;
    private Double averageMatchScore;
    private Map<String, Long> applicationsByStatus;
    private Double topMatchScore;
}
