package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;

/** Loads the exact immutable release pinned to an authenticated Embed session. */
public interface EmbedRuntimeReleasePort {

    /**
     * 查询嵌入式运行时发布版本快照；结果供调用方展示或继续处理。
     *
     * @param sessionId 会话ID，后续用于查询嵌入式运行时发布版本时定位或关联目标
     * @param viewId 视图ID，后续用于查询嵌入式运行时发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于查询嵌入式运行时发布版本时定位或关联目标
     * @return 符合条件的嵌入式运行时发布版本快照结果，供调用方继续处理
     */
    EmbedRuntimeReleaseSnapshot find(
            String sessionId,
            String viewId,
            String releaseId);
}
