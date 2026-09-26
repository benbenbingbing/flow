package com.workflow.contracts.entity.ui.model;

import java.util.List;

/**
 * 表单热修复流程影响快照。
 *
 * @param targets 目标集合，保存在对象中供后续校验、查询或展示
 * @param processVersionCount 流程版本数量，保存在对象中供后续校验、查询或展示
 * @param activeInstanceCount 活动实例数量，保存在对象中供后续校验、查询或展示
 * @param skippedHistoricalInstanceCount {@code skipped}{@code historical}实例数量，保存在对象中供后续校验、查询或展示
 * @param targetHash 目标哈希，保存在对象中供后续校验、查询或展示
 */
public record UiHotfixProcessImpact(
        List<UiHotfixProcessTarget> targets,
        int processVersionCount,
        long activeInstanceCount,
        long skippedHistoricalInstanceCount,
        String targetHash) {

    /**
     * 处理空，并将结果传给后续步骤。
     *
     * @return 处理后的空结果，供调用方继续处理
     */
    public static UiHotfixProcessImpact empty() {
        return new UiHotfixProcessImpact(List.of(), 0, 0L, 0L, "");
    }
}
