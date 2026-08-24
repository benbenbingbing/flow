package com.workflow.migration.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发布候选补偿请求。 */
@Data
public class ReleaseCandidateCompensateRequest {
    @NotBlank
    @Size(min = 5, max = 500)
    private String reason;
}
