package com.jobgraph.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeUploadRequest {

    @NotBlank(message = "Resume text must not be blank")
    private String rawText;

    /** Optional — if provided, we skip LLM extraction for these fields. */
    private String fullName;
    private String email;
    private String phone;
}
