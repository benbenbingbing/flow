package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 热修复目标流程版本预览。
 */
@Value
@Builder
public class UiConfigHotfixTargetPreviewDTO {

    String processVersionHistoryId;
    String processConfigId;
    String processKey;
    String processName;
    Integer processVersion;
    String pinnedReleaseId;
    Integer pinnedReleaseVersion;
    List<String> nodeIds;
    boolean currentStartable;
    long activeInstanceCount;
    long skippedHistoricalInstanceCount;
    boolean compatible;
    List<String> blockers;
}
