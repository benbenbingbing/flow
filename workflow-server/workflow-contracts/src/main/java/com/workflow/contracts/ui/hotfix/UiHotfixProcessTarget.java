package com.workflow.contracts.ui.hotfix;

import java.util.List;

/**
 * 表单热修复可作用的流程发布版本。
 */
public record UiHotfixProcessTarget(
        String processVersionHistoryId,
        String processConfigId,
        String processKey,
        String processName,
        Integer processVersion,
        String deploymentId,
        String pinnedReleaseId,
        Integer pinnedReleaseVersion,
        List<String> nodeIds,
        boolean currentStartable,
        long activeInstanceCount,
        long completedInstanceCount) {
}
