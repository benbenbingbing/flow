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
        String rowKey,
        /** 审批按钮经服务端核验的活动任务 ID；其他表单调用为空。 */
        String taskId,
        /** 审批按钮经服务端核验的流程实例 ID；其他表单调用为空。 */
        String processInstanceId) implements UiInvocationContext {

    /**
     * 保留任务主体绑定加入前的公共构造签名，避免既有 Provider 源码扩展升级后
     * 被迫改造；旧调用不会获得任务能力，新增字段安全地保持为空。
     */
    public FormInvocationContext(
            CommonInvocationContext common,
            EntityDescriptor entity,
            String formId,
            String formKey,
            String formName,
            String mode,
            String recordId,
            String fieldCode,
            String parentRecordId,
            String rowKey) {
        this(common, entity, formId, formKey, formName, mode,
                recordId, fieldCode, parentRecordId, rowKey,
                null, null);
    }
}
