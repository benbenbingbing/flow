package com.workflow.contracts.entity.list.model;

import java.util.Map;

/**
 * 列表页面、弹窗、抽屉和表单选择器共享的运行时上下文。
 *
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
 * @param scene {@code scene}，保存在对象中供后续校验、查询或展示
 * @param sourceEntityCode 来源实体编码，后续用于处理实体列表运行时上下文时定位或关联目标
 * @param sourceRecordId 来源记录ID，后续用于处理实体列表运行时上下文时定位或关联目标
 * @param relationKey 关系键，后续用于授权校验、关联或幂等去重
 * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
 */
public record EntityListRuntimeContext(
        /** 实体编码 */
        String entityCode,
        /** 列表Key */
        String listKey,
        /** 使用场景标识 */
        String scene,
        /** 来源实体编码 */
        String sourceEntityCode,
        /** 来源记录ID */
        String sourceRecordId,
        /** 关联关系Key */
        String relationKey,
        /** 附加参数 */
        Map<String, Object> parameters) {
}
