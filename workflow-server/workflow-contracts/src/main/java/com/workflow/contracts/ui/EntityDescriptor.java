package com.workflow.contracts.ui;

/**
 * 暴露给接口 Provider 的可信实体身份。
 */
public record EntityDescriptor(
        /** 实体定义 ID。 */
        String id,
        /** 实体稳定编码。 */
        String code,
        /** 实体显示名称。 */
        String name,
        /** 实体存储模式。 */
        String storageMode,
        /** 当前数据权限或实体发布版本。 */
        Integer releaseVersion) {
}
