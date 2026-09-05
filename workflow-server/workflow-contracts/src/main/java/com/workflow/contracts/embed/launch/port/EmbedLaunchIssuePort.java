package com.workflow.contracts.embed.launch.port;

import com.workflow.contracts.embed.EmbedApplicationActor;
import com.workflow.contracts.embed.EmbedLaunchCommand;
import com.workflow.contracts.embed.EmbedLaunchIssued;

/** 为已验证应用签发一次性、短期有效的 Embed 启动信息。 */
public interface EmbedLaunchIssuePort {

    /**
     * 校验应用启动请求，并签发其一次性密钥。
     *
     * @param application 已认证的机器应用身份
     * @param command 稳定启动请求业务数据
     * @return 启动信息；验证码仅通过此结果返回
     */
    EmbedLaunchIssued issue(
            EmbedApplicationActor application,
            EmbedLaunchCommand command);
}
