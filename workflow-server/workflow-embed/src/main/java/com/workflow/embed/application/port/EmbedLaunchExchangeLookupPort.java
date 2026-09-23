package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedLaunchExchangeCandidate;
import java.util.Optional;

/** Performs the cheap digest-index lookup before entering the exchange transaction. */
public interface EmbedLaunchExchangeLookupPort {

    /**
     * 按编码摘要查询嵌入式启动记录交换候选人；结果供后续展示或处理。
     *
     * @param launchCodeDigest 启动记录编码摘要，供本方法查询编码摘要时使用
     * @return 匹配的编码摘要；未找到时为空
     */
    Optional<EmbedLaunchExchangeCandidate> findByCodeDigest(String launchCodeDigest);
}
