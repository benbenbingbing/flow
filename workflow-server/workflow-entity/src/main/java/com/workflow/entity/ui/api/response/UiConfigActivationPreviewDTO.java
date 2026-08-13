package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 激活历史 UI 发布版本前的差异预览。
 */
@Value
@Builder
public class UiConfigActivationPreviewDTO {

    String configType;
    String configId;
    String currentReleaseId;
    Integer currentVersion;
    String targetReleaseId;
    Integer targetVersion;
    String riskLevel;
    List<UiConfigHotfixRiskItemDTO> riskItems;
    boolean changed;
    List<String> changedSections;
    List<UiConfigDiffItemDTO> changedItems;
}
