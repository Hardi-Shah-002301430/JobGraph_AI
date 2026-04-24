package com.jobgraph.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String careersUrl;

    /** ASHBY | GREENHOUSE | LEVER | GENERIC */
    private String boardType = "ASHBY";

    private String logoUrl;
    private String industry;
}
