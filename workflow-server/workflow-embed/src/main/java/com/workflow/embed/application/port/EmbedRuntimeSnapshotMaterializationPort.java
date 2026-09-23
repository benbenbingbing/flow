package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedReleaseSnapshot;
import java.time.Instant;

/**
 * 在新 Launch 内把当前 Embed 配置解析为 Flow 最新 ACTIVE 资源并物化内部运行快照。
 *
 * <p>该快照只用于固定一次 Launch/Session，不是管理员需要发布或选择的版本。</p>
 */
public interface EmbedRuntimeSnapshotMaterializationPort {

    /**
     * 处理{@code materialize}，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理{@code materialize}时定位或关联目标
     * @param surfaceType 界面类型标识，决定后续{@code materialize}采用的处理分支
     * @param currentConfigJson 当前配置JSON，供本方法处理{@code materialize}时使用
     * @param materializedBy {@code materialized}，供本方法处理{@code materialize}时使用
     * @param now 当前时间，供本方法处理{@code materialize}时使用
     * @return 处理后的{@code materialize}结果，供调用方继续处理
     */
    EmbedReleaseSnapshot materialize(
            String viewId,
            String surfaceType,
            String currentConfigJson,
            String materializedBy,
            Instant now);
}
