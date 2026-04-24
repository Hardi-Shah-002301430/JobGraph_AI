package com.jobgraph.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CompanyResponse {
    private Long id;
    private String name;
    private String careersUrl;
    private String boardType;
    private String logoUrl;
    private String industry;
    private Boolean active;
    private long jobCount;
}
