package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;

/**
 * 业务数据版本时间线条目。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param versionNo 版本号，保存在对象中供后续校验、查询或展示
 * @param versionTitle 版本{@code title}，后续用于处理实体记录版本摘要时匹配或展示
 * @param scenarioCode {@code scenario}编码，后续用于处理实体记录版本摘要时定位或关联目标
 * @param scenarioName {@code scenario}名称，后续用于处理实体记录版本摘要时匹配或展示
 * @param operationType 操作类型标识，决定后续实体记录版本摘要采用的处理分支
 * @param sourceType 来源类型标识，决定后续实体记录版本摘要采用的处理分支
 * @param businessIntentCode 业务{@code intent}编码，后续用于处理实体记录版本摘要时定位或关联目标
 * @param businessIntentName 业务{@code intent}名称，后续用于处理实体记录版本摘要时匹配或展示
 * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param operatorName 用户名称，后续用于身份匹配或操作展示
 * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
 * @param sourceEntityCode 来源实体编码，后续用于处理实体记录版本摘要时定位或关联目标
 * @param sourceRecordId 来源记录ID，后续用于处理实体记录版本摘要时定位或关联目标
 * @param hasFieldChanges {@code has}字段变更集合，保存在对象中供后续校验、查询或展示
 * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EntityRecordVersionSummary(
        String id,
        Integer versionNo,
        String versionTitle,
        String scenarioCode,
        String scenarioName,
        String operationType,
        String sourceType,
        String businessIntentCode,
        String businessIntentName,
        String operatorId,
        String operatorName,
        String processInstanceId,
        String sourceEntityCode,
        String sourceRecordId,
        boolean hasFieldChanges,
        LocalDateTime createTime) {
}
