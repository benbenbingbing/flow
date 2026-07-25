package com.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基于稳定配置条目 ID 的语义补丁操作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiConfigSemanticPatchOperation {

    private String section;
    private String itemId;
    private String changeType;
    private String path;
    private Object beforeValue;
    private Object afterValue;
    private String riskLevel;
    private String reason;
}
