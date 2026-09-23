package com.workflow.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares stable server-side permission codes required by an API endpoint.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 读取或规范化输入值，供后续计算与比较使用。
     *
     * @return 处理后的值结果，供调用方继续处理
     */
    String[] value();

    /**
     * 判断{@code any}条件是否成立，供调用方选择后续分支。
     *
     * @return {@code any}条件成立时为 true，否则为 false
     */
    boolean any() default false;
}
