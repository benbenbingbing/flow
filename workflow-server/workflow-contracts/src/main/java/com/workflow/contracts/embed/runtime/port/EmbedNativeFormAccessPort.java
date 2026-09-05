package com.workflow.contracts.embed.runtime.port;

import java.util.Map;
import java.util.Optional;

/**
 * Embed 对 Flow 原生 Published Form 的最小访问授权端口。
 *
 * <p>该端口只校验固定目标、原生按钮和记录访问，实际渲染和交互始终调用 Flow
 * 标准运行时 API。</p>
 */
public interface EmbedNativeFormAccessPort {

    /** 按当前映射用户和固定发布版本校验原生 CREATE 按钮。 */
    void requireCreateAction(Target target, String actionKey);

    /**
     * 在当前映射用户权限、数据范围和固定列表发布版本约束下校验 VIEW 记录。
     *
     * @return 未找到或无权时返回空
     */
    Optional<ViewAccess> authorizeView(
            Target target,
            String recordId,
            Map<String, Object> trustedContextFilters);

    /** 由服务端 Session 与不可变 Embed Release 恢复的固定坐标。 */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion,
            String listKey,
            String listReleaseId,
            Integer listReleaseVersion) {
    }

    /** VIEW 授权后原生页面启动所需的最小记录上下文。 */
    record ViewAccess(String processInstanceId) {
    }

    /** 原生操作栏不允许当前动作时抛出。 */
    class OperationNotAllowedException extends RuntimeException {

        public OperationNotAllowedException(String message) {
            super(message);
        }
    }
}
