package com.workflow.contracts.ui;

/**
 * 从列表绑定调用接口操作时使用的可信上下文。
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
