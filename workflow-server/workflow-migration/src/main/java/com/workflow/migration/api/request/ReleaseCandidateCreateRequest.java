package com.workflow.migration.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建应用级发布候选请求。 */
@Data
public class ReleaseCandidateCreateRequest {
    @NotBlank
    @Size(max = 200)
    private String candidateName;
    @Size(max = 1000)
    private String description;
    @NotBlank
    private String sourceImportId;
    @Size(max = 500)
    private List<String> itemIds;
}
