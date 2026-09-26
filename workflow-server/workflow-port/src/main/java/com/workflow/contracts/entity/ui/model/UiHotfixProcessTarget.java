package com.workflow.contracts.entity.ui.model;

import java.util.List;

/**
 * 表单热修复可作用的流程发布版本。
 *
 * @param processVersionHistoryId 流程版本历史ID，后续用于处理界面热修复流程目标时定位或关联目标
 * @param processConfigId 流程配置 ID，后续定位已发布的节点配置
 * @param processKey 流程键，后续用于授权校验、关联或幂等去重
 * @param processName 流程名称，后续用于处理界面热修复流程目标时匹配或展示
 * @param processVersion 流程版本，保存在对象中供后续校验、查询或展示
 * @param deploymentId {@code deployment}ID，后续用于处理界面热修复流程目标时定位或关联目标
 * @param pinnedReleaseId 固定发布版本ID，后续用于处理界面热修复流程目标时定位或关联目标
 * @param pinnedReleaseVersion 固定发布版本，保存在对象中供后续校验、查询或展示
 * @param nodeIds 节点ID 集合，保存在对象中供后续校验、查询或展示
 * @param currentStartable 当前{@code startable}，保存在对象中供后续校验、查询或展示
 * @param activeInstanceCount 活动实例数量，保存在对象中供后续校验、查询或展示
 * @param completedInstanceCount {@code completed}实例数量，保存在对象中供后续校验、查询或展示
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
