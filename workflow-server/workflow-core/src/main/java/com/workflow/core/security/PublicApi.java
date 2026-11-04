package com.workflow.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明普通 MVC 接口允许匿名访问，认证与授权拦截器均按此注解放行，无需额外配置路径白名单。
 *
 * <p>可标注在类或方法上；方法上的访问策略优先于类上的策略。公开类中的方法仍可使用
 * {@link AuthenticatedApi} 或 {@link RequiresPermission} 要求认证。
 * 放行时不会校验 Access Token 或建立普通 JWT 用户上下文，业务逻辑不能依赖该上下文。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
