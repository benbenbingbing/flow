package com.workflow.contracts.entity.ui.context;

import com.workflow.contracts.entity.ui.model.EntityDescriptor;

/**
 * 根据已验证 UI 绑定生成的接口操作调用上下文。
 */
public sealed interface UiInvocationContext
        permits FormInvocationContext,
                ListInvocationContext,
                EntityInvocationContext {

    /**
     * @return 表单、列表和实体调用共享的可信元数据
     *
     * @return 处理后的{@code common}结果，供调用方继续处理
     */
    CommonInvocationContext common();

    /**
     * @return 服务端解析出的实体描述
     *
     * @return 处理后的实体结果，供调用方继续处理
     */
    EntityDescriptor entity();

    /**
     * @return 当前绑定位置编码
     *
     * @return 处理后的使用场景文本，供调用方比较或展示
     */
    default String usage() {
        return common().bindingCode();
    }

    /**
     * @return 当前绑定所有者类型
     *
     * @return 处理后的配置类型文本，供调用方比较或展示
     */
    default String configType() {
        return common().ownerType();
    }

    /**
     * @return 当前绑定所有者 ID
     *
     * @return 处理后的配置ID文本，供调用方比较或展示
     */
    default String configId() {
        return common().ownerId();
    }

    /**
     * @return 当前实体稳定编码
     *
     * @return 处理后的实体编码文本，供调用方比较或展示
     */
    default String entityCode() {
        return entity().code();
    }

    /**
     * @return 当前列表稳定编码，非列表上下文返回 null
     *
     * @return 列出后的键文本，供调用方比较或展示
     */
    default String listKey() {
        return this instanceof ListInvocationContext list
                ? list.listKey()
                : null;
    }

    /**
     * @return 当前认证用户 ID
     *
     * @return 处理后的用户ID文本，供调用方比较或展示
     */
    default String userId() {
        return common().userId();
    }

    /**
     * @return 本次执行使用的 UI 配置发布 ID
     *
     * @return 处理后的发布版本ID文本，供调用方比较或展示
     */
    default String releaseId() {
        return common().releaseId();
    }

    /**
     * @return 本次执行使用的 UI 配置发布版本
     *
     * @return 处理后的发布版本结果，供调用方继续处理
     */
    default Integer releaseVersion() {
        return common().releaseVersion();
    }
}
