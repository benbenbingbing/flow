package com.workflow.contracts.entity.ui.context;

/**
 * 表单、列表和实体接口调用共享的可信元数据。
 *
 * @param extensionId 扩展ID，后续用于处理{@code common}调用上下文时定位或关联目标
 * @param providerOperationCode 提供者操作编码，后续用于处理{@code common}调用上下文时定位或关联目标
 * @param bindingCode 绑定编码，后续用于处理{@code common}调用上下文时定位或关联目标
 * @param ownerType 归属方类型标识，决定后续{@code common}调用上下文采用的处理分支
 * @param ownerId 归属方ID，后续用于处理{@code common}调用上下文时定位或关联目标
 * @param targetType 目标类型标识，决定后续{@code common}调用上下文采用的处理分支
 * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
 * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param tenantId 租户ID，后续用于处理{@code common}调用上下文时定位或关联目标
 * @param organizationId 组织 ID，供后续身份归属和访问判断
 * @param departmentId 部门 ID，供后续身份归属和访问判断
 * @param releaseId 发布版本 ID，后续用于解析固定配置
 * @param releaseVersion 发布版本号，后续用于校验快照一致性
 * @param requestId 请求ID，后续用于处理{@code common}调用上下文时定位或关联目标
 */
public record CommonInvocationContext(
        /** 当前执行的接口扩展 ID。 */
        String extensionId,
        /** Provider 内部实现路由；不作为设计器中的二级接口选项。 */
        String providerOperationCode,
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
