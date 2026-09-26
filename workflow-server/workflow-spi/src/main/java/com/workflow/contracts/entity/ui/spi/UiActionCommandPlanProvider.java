package com.workflow.contracts.entity.ui.spi;

import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.entity.ui.model.UiActionCommandPlan;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.UiProviderArtifactIdentity;

import java.util.Map;

/**
 * 关联内容本地写操作的受控命令计划提供者。
 *
 * <p>该 SPI 仅构造结构化实体变更意图，不能绕过宿主的权限、审计和持久化边界。</p>
 */
public interface UiActionCommandPlanProvider {

    /**
     * 返回扩展实现归属。
     *
     * <p>第三方 Provider 默认视为项目自定义；平台实现必须显式覆盖。</p>
     *
     * @return 处理后的实现来源结果，供调用方继续处理
     */
    default ExtensionImplementationOrigin implementationOrigin() {
        return ExtensionImplementationOrigin.CUSTOM;
    }

    /**
     * @return 与接口扩展 providerCode 对应的稳定注册编码
     *
     * @return 读取后的编码文本，供调用方比较或展示
     */
    String getCode();

    /**
     * @return 配置端展示名称
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    String getDisplayName();

    /**
     * @return 可并存的 Provider 正整数版本；存量实现默认 v1
     *
     * @return 符合条件的界面动作命令方案提供者结果，供调用方继续处理
     */
    default int getVersion() {
        return 1;
    }

    /**
     * @return 本地命令计划实现的 64 位小写十六进制制品摘要
     *
     * @return 读取后的{@code artifact}摘要文本，供调用方比较或展示
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
