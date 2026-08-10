package com.workflow.contracts.ui;

/**
 * 表单、列表和实体接口调用共享的可信元数据。
 */
public record CommonInvocationContext(
        /** 当前执行的接口服务 ID。 */
        String serviceId,
        /** 当前执行的接口操作编码。 */
        String operationCode,
        /** 绑定位置编码，例如 FIELD_OPTIONS、LIST_COLUMN。 */
        String bindingCode,
        /** 绑定所有者类型：FORM、LIST 或 ENTITY。 */
        String ownerType,
        /** 绑定所有者的数据库主键。 */
        String ownerId,
        /** 精确目标类型，例如 FIELD、COLUMN、BUTTON 或 OWNER。 */
        String targetType,
        /** 精确目标的稳定编码，例如字段编码或按钮编码。 */
        String targetKey,
        /** 当前认证用户 ID。 */
        String userId,
        /** 当前认证用户名。 */
        String username,
        /** 当前租户 ID。 */
        String tenantId,
        /** 当前组织 ID。 */
        String organizationId,
        /** 当前部门 ID。 */
        String departmentId,
        /** 本次执行使用的 UI 配置发布 ID。 */
        String releaseId,
        /** 本次执行使用的 UI 配置发布版本。 */
        Integer releaseVersion,
        /** 本次接口调用的请求追踪 ID。 */
        String requestId) {
}
