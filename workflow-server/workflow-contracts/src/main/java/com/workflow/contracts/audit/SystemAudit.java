package com.workflow.contracts.audit;

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

    AuditModule module();

    AuditAction action();

    String operation();

    AuditRiskLevel risk() default AuditRiskLevel.MEDIUM;

    /**
     * 审计入队失败时是否阻断业务事务。
     */
    boolean required() default false;

    String targetType() default "";

    /**
     * 目标 ID 所在参数下标；小于 0 时由切面从参数或返回值中提取。
     */
    int targetIdArg() default -1;

    boolean captureArguments() default false;

    boolean captureResult() default false;
}
