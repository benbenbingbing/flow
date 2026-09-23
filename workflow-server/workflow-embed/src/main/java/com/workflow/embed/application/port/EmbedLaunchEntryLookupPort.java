package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedLaunchEntrySnapshot;
import java.util.Optional;

/** 查询动态 iframe Entry 所需的最小、非敏感 Launch 安全投影。 */
public interface EmbedLaunchEntryLookupPort {

    /**
     * 查询嵌入式启动记录入口快照；结果供调用方展示或继续处理。
     *
     * @param launchId 启动记录ID，后续用于查询嵌入式启动记录入口查找时定位或关联目标
     * @return 匹配的嵌入式启动记录入口查找；未找到时为空
     */
    Optional<EmbedLaunchEntrySnapshot> find(String launchId);
}
