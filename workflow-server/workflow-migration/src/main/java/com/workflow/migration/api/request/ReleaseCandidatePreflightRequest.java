package com.workflow.migration.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发布候选统一预检请求，expectedRevision 用于阻止多标签覆盖。 */
@Data
public class ReleaseCandidatePreflightRequest {
    @NotNull
    private Integer expectedRevision;
}
