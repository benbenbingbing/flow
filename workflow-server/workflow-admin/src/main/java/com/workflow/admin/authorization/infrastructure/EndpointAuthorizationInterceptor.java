package com.workflow.admin.authorization.infrastructure;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.PublicApi;
import com.workflow.core.security.RequiresPermission;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;

/**
 * Enforces an explicit access policy on every mapped API handler.
 */
@Component
public class EndpointAuthorizationInterceptor implements HandlerInterceptor {

    private final SysMenuMapper menuMapper;
    private final CurrentUserRoleService currentUserRoleService;

    @Autowired
    public EndpointAuthorizationInterceptor(
            ObjectProvider<SysMenuMapper> menuMapperProvider,
            ObjectProvider<CurrentUserRoleService> currentUserRoleServiceProvider) {
        this(
                menuMapperProvider.getIfAvailable(),
                currentUserRoleServiceProvider.getIfAvailable());
    }

    public EndpointAuthorizationInterceptor(
            SysMenuMapper menuMapper,
            CurrentUserRoleService currentUserRoleService) {
        this.menuMapper = menuMapper;
        this.currentUserRoleService = currentUserRoleService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (findAnnotation(handlerMethod, PublicApi.class) != null) {
            return true;
        }

        if (UserContext.getUserId() == null || UserContext.getUserId().isBlank()) {
            throw new ForbiddenException("用户未登录");
        }

        RequiresPermission permission = findAnnotation(handlerMethod, RequiresPermission.class);
        if (permission != null) {
            requirePermission(permission);
            return true;
        }

        if (findAnnotation(handlerMethod, AuthenticatedApi.class) != null) {
            return true;
        }

        throw new ForbiddenException("接口未配置访问策略");
    }

    private void requirePermission(RequiresPermission requirement) {
        String[] required = Arrays.stream(requirement.value())
                .filter(value -> value != null && !value.isBlank())
                .toArray(String[]::new);
        if (required.length == 0) {
            throw new ForbiddenException("接口权限配置无效");
        }
        if (menuMapper == null || currentUserRoleService == null) {
            throw new ForbiddenException("权限服务暂不可用");
        }
        if (currentUserRoleService.isSuperAdmin()) {
            return;
        }

        String userId = UserContext.getUserId();
        Set<String> selected = userId == null ? Set.of() : menuMapper.selectPermsByUserId(userId);
        Set<String> granted = selected == null ? Set.of() : selected;
        boolean allowed = requirement.any()
                ? Arrays.stream(required).anyMatch(granted::contains)
                : Arrays.stream(required).allMatch(granted::contains);
        if (!allowed) {
            throw new ForbiddenException("没有权限访问该接口");
        }
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(
            HandlerMethod handlerMethod,
            Class<A> annotationType) {
        A methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), annotationType);
        return methodAnnotation != null
                ? methodAnnotation
                : AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), annotationType);
    }
}
