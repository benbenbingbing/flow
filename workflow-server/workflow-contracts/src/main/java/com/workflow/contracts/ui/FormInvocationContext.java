package com.workflow.contracts.ui;

/**
 * 从表单绑定调用接口操作时使用的可信上下文。
 */
public record FormInvocationContext(
        /** 表单、列表和实体调用共享的可信元数据。 */
        CommonInvocationContext common,
        /** 当前表单所属实体的可信描述。 */
        EntityDescriptor entity,
        /** 当前表单 ID。 */
        String formId,
        /** 当前表单稳定编码。 */
        String formKey,
        /** 当前表单名称。 */
        String formName,
        /** 当前表单模式，例如 create、edit、view、approve。 */
        String mode,
        /** 当前业务记录 ID；新增场景可为空。 */
        String recordId,
        /** 当前字段编码；表单级绑定可为空。 */
        String fieldCode,
        /** 子表单或明细行所属的父记录 ID。 */
        String parentRecordId,
        /** 子表单或明细行的稳定行标识。 */
        String rowKey) implements UiInvocationContext {
}
