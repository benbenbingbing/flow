package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedReleaseSnapshot;
import java.time.Instant;

/**
 * 在新 Launch 内把当前 Embed 配置解析为 Flow 最新 ACTIVE 资源并物化内部运行快照。
 *
 * <p>该快照只用于固定一次 Launch/Session，不是管理员需要发布或选择的版本。</p>
 */
public interface EmbedRuntimeSnapshotMaterializationPort {

    EmbedReleaseSnapshot materialize(
            String viewId,
            String surfaceType,
            String currentConfigJson,
            String materializedBy,
            Instant now);
}
