package com.workflow.migration.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发布或续跑请求，修订号、候选哈希和幂等键缺一不可。 */
@Data
public class ReleaseCandidateExecuteRequest {
    @NotNull
    private Integer expectedRevision;
    @NotBlank
    @Size(max = 64)
    private String candidateHash;
    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
}
