package com.workflow.contracts.ui;

import com.workflow.contracts.entity.list.DataScopePlan;

import java.util.Map;

/**
 * UI 数据源提供者。
 * 由具体实现提供按编码标识的 UI 数据源，并在数据范围计划约束下执行数据查询。
 */
public interface UiDataSourceProvider {

    /**
     * 返回数据源编码。
     *
     * @return 数据源编码
     */
    String getCode();

    /**
     * 返回数据源展示名称。
     *
     * @return 展示名称
     */
    String getDisplayName();

    /**
     * 返回可并存的 Provider 版本。存量实现默认视为 v1。
     *
     * @return 正整数版本号
     */
    default int getVersion() {
        return 1;
    }

    /**
     * 返回当前可执行制品摘要，供宿主发布版本精确固定实现。
     *
     * <p>存量实现默认对自身 class 字节码计算 SHA-256；实现依赖额外脚本、模型
     * 或资源时应覆盖此方法并返回整个受审制品的稳定摘要。</p>
     *
     * @return 64 位小写十六进制摘要
     */
    default String getArtifactDigest() {
        return UiProviderArtifactIdentity.defaultDigest(
                getClass(), getVersion());
    }

    /**
     * 返回该数据源的配置项 Schema。
     *
     * @return 配置项 Schema，默认空
     */
    default Map<String, Object> configurationSchema() {
        return Map.of();
    }

    /**
     * 执行数据源查询。
     *
     * @param context        UI 数据源上下文
     * @param dataScopePlan  数据范围查询计划
     * @param configuration  数据源配置
     * @param input          调用输入
     * @return 查询结果
     */
    Object execute(
            UiInvocationContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> configuration,
            Map<String, Object> input);
}
