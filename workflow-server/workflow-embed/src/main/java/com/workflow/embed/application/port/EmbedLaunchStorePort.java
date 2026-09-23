package com.workflow.embed.application.port;

import com.workflow.embed.domain.PersistedEmbedLaunch;

/** Persists a launch containing only credential digests and protected context. */
public interface EmbedLaunchStorePort {

    /**
     * 插入嵌入式启动记录{@code store}；后续读取或执行将使用更新后的状态。
     *
     * @param launch 启动记录，供本方法插入嵌入式启动记录{@code store}时使用
     */
    void insert(PersistedEmbedLaunch launch);
}
