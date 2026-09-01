package com.workflow.config;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.EmbedRequestUserContextPort;
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
