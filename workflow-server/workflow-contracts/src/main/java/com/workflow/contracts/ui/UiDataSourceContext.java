package com.workflow.contracts.ui;

import java.util.Map;

/**
 * UI 数据源上下文。
 * 描述一次 UI 数据源调用的用途、实体/列表定位、当前用户及租户环境，以及关联的配置与发布版本信息。
 */
public record UiDataSourceContext(
        /** 调用用途标识 */
        String usage,
        /** 实体编码 */
        String entityCode,
        /** 列表Key */
        String listKey,
        /** 用户ID */
        String userId,
        /** 运行时上下文附加参数 */
        Map<String, Object> runtimeContext,
        /** 用户名 */
        String username,
        /** 租户ID */
        String tenantId,
        /** 组织ID */
        String organizationId,
        /** 部门ID */
        String departmentId,
        /** 配置类型 */
        String configType,
        /** 配置ID */
        String configId,
        /** 发布ID */
        String releaseId,
        /** 发布版本号 */
        Integer releaseVersion) {

    /**
     * 便捷构造方法：仅传入核心字段，其余环境字段置空。
     *
     * @param usage          调用用途标识
     * @param entityCode     实体编码
     * @param listKey        列表Key
     * @param userId         用户ID
     * @param runtimeContext 运行时上下文附加参数
     */
    public UiDataSourceContext(
            String usage,
            String entityCode,
            String listKey,
            String userId,
            Map<String, Object> runtimeContext) {
        this(
                usage,
                entityCode,
                listKey,
                userId,
                runtimeContext,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
