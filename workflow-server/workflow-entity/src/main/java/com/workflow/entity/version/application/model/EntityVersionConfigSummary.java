package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;

/**
 * 数据版本管理列表项。
 *
 * @param entityId 实体ID，后续用于处理实体版本配置摘要时定位或关联目标
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param entityName 实体名称，后续用于处理实体版本配置摘要时匹配或展示
 * @param enabled 启用，保存在对象中供后续校验、查询或展示
 * @param revision 修订版本，保存在对象中供后续校验、查询或展示
 * @param runtimeEnabled 运行时启用，保存在对象中供后续校验、查询或展示
 * @param triggerCount 触发条件数量，保存在对象中供后续校验、查询或展示
 * @param scopeRelationCount 作用域关系数量，保存在对象中供后续校验、查询或展示
 * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EntityVersionConfigSummary(
        String entityId,
        String entityCode,
        String entityName,
        boolean enabled,
        Integer revision,
        boolean runtimeEnabled,
        int triggerCount,
        int scopeRelationCount,
        LocalDateTime updateTime) {
}
