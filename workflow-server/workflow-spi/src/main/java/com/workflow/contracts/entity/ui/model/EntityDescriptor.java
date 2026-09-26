package com.workflow.contracts.entity.ui.model;

/**
 * 暴露给接口 Provider 的可信实体身份。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param code 业务编码，供后续匹配和引用
 * @param name 展示名称，供界面或日志识别
 * @param storageMode 存储模式标识，决定后续实体描述采用的处理分支
 * @param releaseVersion 发布版本号，后续用于校验快照一致性
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
