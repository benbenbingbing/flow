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

    /**
     * 按当前映射用户和固定发布版本校验原生 CREATE 按钮。
     *
     * @param target 目标，供本方法校验并获取创建动作时使用
     * @param actionKey 动作键，后续用于授权校验、关联或幂等去重
     */
    void requireCreateAction(Target target, String actionKey);

    /**
     * 在当前映射用户权限、数据范围和固定列表发布版本约束下校验 VIEW 记录。
     *
     * @param target 目标，供本方法处理授权视图时使用
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param trustedContextFilters 可信上下文过滤条件，供本方法处理授权视图时使用
     * @return 未找到或无权时返回空
     */
    Optional<ViewAccess> authorizeView(
            Target target,
            String recordId,
            Map<String, Object> trustedContextFilters);

    /**
     * 由服务端 Session 与不可变 Embed Release 恢复的固定坐标。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理目标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listReleaseId 列表发布版本ID，后续用于处理目标时定位或关联目标
     * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
     */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion,
            String listKey,
            String listReleaseId,
            Integer listReleaseVersion) {
    }

    /**
     * VIEW 授权后原生页面启动所需的最小记录上下文。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     */
    record ViewAccess(String processInstanceId) {
    }

    /** 原生操作栏不允许当前动作时抛出。 */
    class OperationNotAllowedException extends RuntimeException {

        /**
         * 初始化操作非允许异常，保存构造参数供后续方法使用。
         *
         * @param message 消息，保存在对象中供后续校验、查询或展示
         */
        public OperationNotAllowedException(String message) {
            super(message);
        }
    }
}
