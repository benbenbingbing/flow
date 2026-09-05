package com.workflow.contracts.entity.ui.spi;

import com.workflow.contracts.ui.UiActionCommandPlan;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.contracts.ui.UiProviderArtifactIdentity;

import java.util.Map;

/**
 * 关联内容本地写操作的受控命令计划提供者。
 *
 * <p>该 SPI 仅构造结构化实体变更意图，不能绕过宿主的权限、审计和持久化边界。</p>
 */
public interface UiActionCommandPlanProvider {

    /** @return 与接口服务 providerCode 对应的稳定注册编码 */
    String getCode();

    /** @return 配置端展示名称 */
    String getDisplayName();

    /** @return 可并存的 Provider 正整数版本；存量实现默认 v1 */
    default int getVersion() {
        return 1;
    }

    /** @return 本地命令计划实现的 64 位小写十六进制制品摘要 */
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
