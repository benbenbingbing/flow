package com.workflow.entity.data.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessForbiddenException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 将已通过发布按钮、权限和表单校验的重新发起意图传到同步写入管道。
 * 不把授权标记放在客户端 JSON 中，防止其他更新入口仅凭请求字段启动新流程。
 * 作用域绑定用户、实体、记录和旧实例；异常或嵌套调用结束后恢复原上下文。
 */
final class EntityProcessRestartContext {
    private static final ThreadLocal<Authorization> CURRENT = new ThreadLocal<>();

    private EntityProcessRestartContext() {}

    static <T> T execute(String entityCode, String recordId, String previousInstanceId, Supplier<T> write) {
        Authorization previous = CURRENT.get();
        CURRENT.set(new Authorization(entityCode, recordId, previousInstanceId, UserContext.getUserId()));
        try {
            return write.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** 仅允许本次已鉴权的原记录写入；仍需在记录锁内重新校验流程事实。 */
    static void require(String entityCode, String recordId, String previousInstanceId) {
        if (previousInstanceId == null || previousInstanceId.isBlank()
                || !Objects.equals(CURRENT.get(), new Authorization(
                entityCode, recordId, previousInstanceId, UserContext.getUserId()))) {
            throw new BusinessForbiddenException("ENTITY_PROCESS_RESTART_NOT_AUTHORIZED",
                    "请通过已启用的重新发起按钮提交");
        }
    }

    private record Authorization(String entityCode, String recordId, String previousInstanceId, String userId) {}
}
