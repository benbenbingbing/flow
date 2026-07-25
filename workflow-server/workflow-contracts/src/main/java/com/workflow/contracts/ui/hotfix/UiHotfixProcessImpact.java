package com.workflow.contracts.ui.hotfix;

import java.util.List;

/**
 * 表单热修复流程影响快照。
 */
public record UiHotfixProcessImpact(
        List<UiHotfixProcessTarget> targets,
        int processVersionCount,
        long activeInstanceCount,
        long skippedHistoricalInstanceCount,
        String targetHash) {

    public static UiHotfixProcessImpact empty() {
        return new UiHotfixProcessImpact(List.of(), 0, 0L, 0L, "");
    }
}
