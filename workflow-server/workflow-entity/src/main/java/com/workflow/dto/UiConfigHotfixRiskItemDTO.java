package com.workflow.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 热修复变更风险条目。
 */
@Value
@Builder
public class UiConfigHotfixRiskItemDTO {

    String section;
    String itemId;
    String path;
    String riskLevel;
    String reason;
}
