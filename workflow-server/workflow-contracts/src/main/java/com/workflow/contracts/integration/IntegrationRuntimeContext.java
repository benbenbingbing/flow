package com.workflow.contracts.integration;

import com.workflow.contracts.ui.UiInvocationContext;

/**
 * 连接器只读运行上下文。
 *
 * <p>底层直接持有接口服务的强类型调用上下文，便捷方法仅用于连接器模板读取，
 * 不再维护另一套可与 UI 上下文产生偏差的元数据。</p>
 */
public record IntegrationRuntimeContext(
        /** 服务端从已验证绑定生成的强类型调用上下文。 */
        UiInvocationContext invocationContext) {

    /** @return 接口服务 ID */
    public String serviceId() {
        return invocationContext.common().serviceId();
    }

    /** @return 接口操作编码 */
    public String operationCode() {
        return invocationContext.common().operationCode();
    }

    /** @return 绑定位置编码 */
    public String usage() {
        return invocationContext.common().bindingCode();
    }

    /** @return 绑定所有者类型 */
    public String configType() {
        return invocationContext.common().ownerType();
    }

    /** @return 绑定所有者 ID */
    public String configId() {
        return invocationContext.common().ownerId();
    }

    /** @return 精确目标类型 */
    public String targetType() {
        return invocationContext.common().targetType();
    }

    /** @return 精确目标稳定编码 */
    public String targetKey() {
        return invocationContext.common().targetKey();
    }

    /** @return UI 配置发布 ID */
    public String releaseId() {
        return invocationContext.common().releaseId();
    }

    /** @return UI 配置发布版本 */
    public Integer releaseVersion() {
        return invocationContext.common().releaseVersion();
    }

    /** @return 实体定义 ID */
    public String entityId() {
        return invocationContext.entity().id();
    }

    /** @return 实体编码 */
    public String entityCode() {
        return invocationContext.entity().code();
    }

    /** @return 列表编码，非列表上下文返回 null */
    public String listKey() {
        return invocationContext.listKey();
    }

    /** @return 当前认证用户 ID */
    public String userId() {
        return invocationContext.common().userId();
    }

    /** @return 当前认证用户名 */
    public String username() {
        return invocationContext.common().username();
    }

    /** @return 当前租户 ID */
    public String tenantId() {
        return invocationContext.common().tenantId();
    }

    /** @return 当前组织 ID */
    public String organizationId() {
        return invocationContext.common().organizationId();
    }

    /** @return 当前部门 ID */
    public String departmentId() {
        return invocationContext.common().departmentId();
    }
}
