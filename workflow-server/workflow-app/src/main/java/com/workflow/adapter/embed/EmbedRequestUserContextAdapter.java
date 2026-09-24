package com.workflow.adapter.embed;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.runtime.port.EmbedRequestUserContextPort;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将已认证的 Embed Actor 桥接到现有业务服务使用的 {@link UserContext}。
 *
 * <p>适配器不负责认证；它只在专属过滤器已经完成 Session、Binding、用户状态和安全版本校验后
 * 建立请求线程上下文。Scope 关闭时恢复进入前的值，避免容器线程复用造成用户串号。</p>
 */
@Component
public class EmbedRequestUserContextAdapter
        implements EmbedRequestUserContextPort {

    /**
     * 处理打开，并将结果传给后续步骤。
     *
     * @param flowUserId 流程用户ID，后续用于处理打开时定位或关联目标
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param embedSessionId 嵌入式会话ID，后续用于处理打开时定位或关联目标
     * @return 处理后的打开结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public Scope open(
            String flowUserId,
            String username,
            String embedSessionId) {
        if (!StringUtils.hasText(flowUserId)
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(embedSessionId)) {
            throw new IllegalArgumentException(
                    "Embed mapped Flow user context is incomplete");
        }
        String previousUserId = UserContext.getUserId();
        String previousUsername = UserContext.getUsername();
        String previousSessionId = UserContext.getSessionId();
        UserContext.setCurrentUser(
                flowUserId,
                username,
                embedSessionId);
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            UserContext.clear();
            if (previousUserId != null || previousUsername != null
                    || previousSessionId != null) {
                UserContext.setCurrentUser(
                        previousUserId,
                        previousUsername,
                        previousSessionId);
            }
        };
    }
}
