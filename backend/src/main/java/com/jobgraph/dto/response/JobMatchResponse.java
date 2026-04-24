package com.jobgraph.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder
public class JobMatchResponse {
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String location;
    private String employmentType;
    private Instant postedAt;

    /** Which of the user's resumes produced this score. */
    private Long resumeId;
    /** Human-friendly label for the resume ("Hardi Shah — Platform Engineer"). */
    private String resumeLabel;

    private Double overallScore;
    private Double skillScore;
    private Double experienceScore;
    private Double educationScore;
    private Double industryScore;
    private Double locationScore;

    private String skillDetail;
    private String experienceDetail;
    private String educationDetail;
    private String industryDetail;
    private String locationDetail;
    private String resumeTips;

    private String trackingStatus;  // nullable
}