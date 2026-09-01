package com.workflow.contracts.embed;

import java.util.Map;
import java.util.Optional;

/**
 * Embed 对 Flow 原生 Published Form 的最小访问授权端口。
 *
 * <p>该端口只校验固定表单目标、原生按钮和 VIEW 记录访问，不读取、转换或
 * 白名单化任何字段、组件、布局、选项或弹层配置。iframe 的实际渲染和交互始终
 * 调用 Flow 标准运行时 API。</p>
 */
public interface EmbedNativeFormAccessPort {

    /** 按当前映射用户和固定发布版本校验原生 CREATE 按钮。 */
    void requireCreateAction(Target target, String actionKey);

    /**
     * 在当前映射用户对象权限、DataScope、固定 List Release 和固定过滤条件的
     * 交集内校验 VIEW 记录；不存在与无权统一返回空。
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

    /** 原生操作栏不允许当前动作。 */
    class OperationNotAllowedException extends RuntimeException {

        public OperationNotAllowedException(String message) {
            super(message);
        }
    }
}
