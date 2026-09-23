package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedFlowUser;
import java.util.Optional;

/** Reads Flow user state without exposing admin persistence models to the application layer. */
public interface EmbedFlowUserPort {

    /**
     * 按ID查询嵌入式流程用户；结果供后续展示或处理。
     *
     * @param flowUserId 流程用户ID，后续用于查询ID时定位或关联目标
     * @return 匹配的ID；未找到时为空
     */
    Optional<EmbedFlowUser> findById(String flowUserId);
}
