package com.workflow.contracts.ui;

/**
 * 根据已验证 UI 绑定生成的接口操作调用上下文。
 */
public sealed interface UiInvocationContext
        permits FormInvocationContext,
                ListInvocationContext,
                EntityInvocationContext {

    /** @return 表单、列表和实体调用共享的可信元数据 */
    CommonInvocationContext common();

    /** @return 服务端解析出的实体描述 */
    EntityDescriptor entity();

    /** @return 当前绑定位置编码 */
    default String usage() {
        return common().bindingCode();
    }

    /** @return 当前绑定所有者类型 */
    default String configType() {
        return common().ownerType();
    }

    /** @return 当前绑定所有者 ID */
    default String configId() {
        return common().ownerId();
    }

    /** @return 当前实体稳定编码 */
    default String entityCode() {
        return entity().code();
    }

    /** @return 当前列表稳定编码，非列表上下文返回 null */
    default String listKey() {
        return this instanceof ListInvocationContext list
                ? list.listKey()
                : null;
    }

    /** @return 当前认证用户 ID */
    default String userId() {
        return common().userId();
    }

    /** @return 本次执行使用的 UI 配置发布 ID */
    default String releaseId() {
        return common().releaseId();
    }

    /** @return 本次执行使用的 UI 配置发布版本 */
    default Integer releaseVersion() {
        return common().releaseVersion();
    }
}
