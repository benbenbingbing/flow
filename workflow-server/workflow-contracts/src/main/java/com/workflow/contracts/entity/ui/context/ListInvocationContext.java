package com.workflow.contracts.entity.ui.context;

import com.workflow.contracts.entity.ui.model.EntityDescriptor;

/**
 * 从列表绑定调用接口操作时使用的可信上下文。
 *
 * @param common {@code common}，保存在对象中供后续校验、查询或展示
 * @param entity 实体，保存在对象中供后续校验、查询或展示
 * @param listId 列表ID，后续用于处理列表调用上下文时定位或关联目标
 * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
 * @param listName 列表名称，后续用于处理列表调用上下文时匹配或展示
 * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
 * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
 * @param fieldCode 字段编码，后续用于处理列表调用上下文时定位或关联目标
 * @param scene {@code scene}，保存在对象中供后续校验、查询或展示
 */
public record ListInvocationContext(
        /** 表单、列表和实体调用共享的可信元数据。 */
        CommonInvocationContext common,
        /** 当前列表所属实体的可信描述。 */
        EntityDescriptor entity,
        /** 当前列表配置 ID。 */
        String listId,
        /** 当前列表稳定编码。 */
        String listKey,
        /** 当前列表名称。 */
        String listName,
        /** 当前页码。 */
        Integer pageNum,
        /** 当前每页条数。 */
        Integer pageSize,
        /** 当前列表列字段编码；列表级绑定可为空。 */
        String fieldCode,
        /** 当前列表运行场景，例如 PAGE、DIALOG、FORM_PICKER。 */
        String scene) implements UiInvocationContext {
}
