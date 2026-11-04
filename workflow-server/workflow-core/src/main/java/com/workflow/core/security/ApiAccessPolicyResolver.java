package com.workflow.core.security;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * 统一解析 MVC 接口的公开访问策略，供认证和授权拦截器使用，避免两层放行规则不一致。
 */
public final class ApiAccessPolicyResolver {

    private ApiAccessPolicyResolver() {
    }

    /**
     * 判断接口是否允许匿名访问；方法级策略覆盖类级策略，未声明公开策略时仍需认证。
     *
     * @param handlerMethod MVC 已匹配的控制器方法，结合方法与所属类的注解确定访问策略
     * @return 有效策略为 {@link PublicApi} 时返回 true，认证与授权拦截器据此跳过校验
     */
    public static boolean isPublic(HandlerMethod handlerMethod) {
        if (AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), PublicApi.class) != null) {
            return true;
        }
        // 公开控制器仍可逐个收紧接口权限，不能让类级 PublicApi 覆盖方法上的认证要求。
        if (AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), AuthenticatedApi.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequiresPermission.class) != null) {
            return false;
        }
        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), PublicApi.class) != null;
    }
}
