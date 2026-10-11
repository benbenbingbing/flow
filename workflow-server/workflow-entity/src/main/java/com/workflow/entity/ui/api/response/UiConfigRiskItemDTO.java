package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/**
 * UI 配置发布变更风险条目，供普通发布和列表历史激活预览展示。
 */
@Value
@Builder
public class UiConfigRiskItemDTO {

    String section;
    String itemId;
    String path;
    String riskLevel;
    String reason;
}
