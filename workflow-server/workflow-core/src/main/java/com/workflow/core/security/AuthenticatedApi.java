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

    boolean objectAuthorization() default false;
}
