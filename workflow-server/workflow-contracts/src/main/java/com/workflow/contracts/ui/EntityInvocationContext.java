package com.workflow.contracts.ui;

/**
 * 从实体绑定调用接口操作时使用的可信上下文。
 */
public record EntityInvocationContext(
        /** 表单、列表和实体调用共享的可信元数据。 */
        CommonInvocationContext common,
        /** 当前实体的可信描述。 */
        EntityDescriptor entity,
        /** 实体操作类型：CREATE、UPDATE、DELETE 或 SELECT。 */
        String operation,
        /** 当前业务记录 ID；新增场景可为空。 */
        String recordId) implements UiInvocationContext {
}
