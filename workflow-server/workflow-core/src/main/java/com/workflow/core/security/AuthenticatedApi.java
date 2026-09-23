package com.workflow.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API endpoint that requires a valid authenticated user.
 *
 * <p>State-changing endpoints are denied by default. They must either use
 * {@link RequiresPermission} or explicitly declare that the controller/service
 * performs resource-level authorization.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticatedApi {

    /**
     * 判断对象授权条件是否成立，供调用方选择后续分支。
     *
     * @return 对象授权条件成立时为 true，否则为 false
     */
    boolean objectAuthorization() default false;
}
