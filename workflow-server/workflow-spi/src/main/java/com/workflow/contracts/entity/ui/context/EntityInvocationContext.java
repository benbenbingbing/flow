package com.workflow.contracts.entity.ui.context;

import com.workflow.contracts.entity.ui.model.EntityDescriptor;

/**
 * 从实体绑定调用接口操作时使用的可信上下文。
 *
 * @param common {@code common}，保存在对象中供后续校验、查询或展示
 * @param entity 实体，保存在对象中供后续校验、查询或展示
 * @param operation 操作标识，决定后续实体调用上下文采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
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
