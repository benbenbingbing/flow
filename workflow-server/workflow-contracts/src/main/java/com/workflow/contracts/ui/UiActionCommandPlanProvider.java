package com.workflow.contracts.ui;

import java.util.Map;

/**
 * 关联内容本地写操作的受控命令计划提供者。
 *
 * <p>实现只能返回结构化实体变更意图，不能获得数据库、权限对象、幂等键或
 * 客户端任意补丁。平台会在执行前重新校验目标实体、记录数据范围、字段权限
 * 和实体变更策略，并统一生成审计与持久化回执。</p>
 */
public interface UiActionCommandPlanProvider {

    /** @return 与接口服务 providerCode 对应的稳定注册编码 */
    String getCode();

    /** @return 配置端展示名称 */
    String getDisplayName();

    /** @return 可并存的 Provider 正整数版本，存量实现默认 v1 */
    default int getVersion() {
        return 1;
    }

    /**
     * 返回本地命令计划实现的制品摘要，供宿主发布版本精确固定。
     *
     * @return 64 位小写十六进制摘要
     */
    default String getArtifactDigest() {
        return UiProviderArtifactIdentity.defaultDigest(
                getClass(), getVersion());
    }

    /**
     * 根据发布快照中的配置和服务端构造的最小输入生成变更计划。
     *
     * @param context 已验证的宿主调用上下文
     * @param configuration 已钉定接口操作配置
     * @param input 仅由发布字段映射产生的最小输入
     * @return 有界、强类型的实体变更计划
     */
    UiActionCommandPlan plan(
            UiInvocationContext context,
            Map<String, Object> configuration,
            Map<String, Object> input);
}
