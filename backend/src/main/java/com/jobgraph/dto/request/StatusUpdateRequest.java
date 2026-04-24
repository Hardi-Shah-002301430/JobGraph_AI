package com.jobgraph.dto.request;

import com.jobgraph.model.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateRequest {

    @NotNull
    private ApplicationStatus status;

    private String notes;
}
