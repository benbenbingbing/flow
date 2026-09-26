package com.workflow.contracts.audit.annotation;

import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.port.SystemAuditPort;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写入系统操作审计的应用服务方法。
 *
 * <p>复杂发布、迁移和批量场景可以直接使用 {@link SystemAuditPort}，
 * 避免在注解中引入难以维护的表达式。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemAudit {

    /**
     * 处理{@code module}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code module}结果，供调用方继续处理
     */
    AuditModule module();

    /**
     * 处理动作，并将结果传给后续步骤。
     *
     * @return 处理后的动作结果，供调用方继续处理
     */
    AuditAction action();

    /**
     * 生成操作文本，供后续匹配或展示。
     *
     * @return 处理后的操作文本，供调用方比较或展示
     */
    String operation();

    /**
     * 处理风险，并将结果传给后续步骤。
     *
     * @return 处理后的风险结果，供调用方继续处理
     */
    AuditRiskLevel risk() default AuditRiskLevel.MEDIUM;

    /**
     * 审计入队失败时是否阻断业务事务。
     *
     * @return 必填条件成立时为 true，否则为 false
     */
    boolean required() default false;

    /**
     * 生成目标类型文本，供后续匹配或展示。
     *
     * @return 处理后的目标类型文本，供调用方比较或展示
     */
    String targetType() default "";

    /**
     * 目标 ID 所在参数下标；小于 0 时由切面从参数或返回值中提取。
     *
     * @return 处理后的目标ID{@code arg}结果，供调用方继续处理
     */
    int targetIdArg() default -1;

    /**
     * 捕获{@code arguments}；结果供调用方的后续步骤使用。
     *
     * @return {@code arguments}条件成立时为 true，否则为 false
     */
    boolean captureArguments() default false;

    /**
     * 捕获系统审计结果；结果供调用方的后续步骤使用。
     *
     * @return 系统审计结果条件成立时为 true，否则为 false
     */
    boolean captureResult() default false;
}
