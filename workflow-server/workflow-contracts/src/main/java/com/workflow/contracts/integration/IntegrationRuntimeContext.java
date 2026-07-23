package com.workflow.contracts.integration;

/**
 * 集成调用运行时上下文。
 * 描述本次集成调用的来源、关联配置/发布版本，以及当前用户、租户等环境信息。
 */
public record IntegrationRuntimeContext(
        /** 调用来源ID */
        String sourceId,
        /** 调用用途标识 */
        String usage,
        /** 配置类型 */
        String configType,
        /** 配置ID */
        String configId,
        /** 发布ID */
        String releaseId,
        /** 发布版本号 */
        Integer releaseVersion,
        /** 实体ID */
        String entityId,
        /** 实体编码 */
        String entityCode,
        /** 列表Key */
        String listKey,
        /** 用户ID */
        String userId,
        /** 用户名 */
        String username,
        /** 租户ID */
        String tenantId,
        /** 组织ID */
        String organizationId,
        /** 部门ID */
        String departmentId) {
}
