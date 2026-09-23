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

    /**
     * 初始化接口端点授权{@code interceptor}，保存构造参数供后续方法使用。
     *
     * @param menuMapperProvider 菜单映射器提供者，保存在对象中供后续校验、查询或展示
     * @param currentUserRoleServiceProvider 当前用户角色服务提供者，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EndpointAuthorizationInterceptor(
            ObjectProvider<SysMenuMapper> menuMapperProvider,
            ObjectProvider<CurrentUserRoleService> currentUserRoleServiceProvider) {
        this(
                menuMapperProvider.getIfAvailable(),
                currentUserRoleServiceProvider.getIfAvailable());
    }

    /**
     * 初始化接口端点授权{@code interceptor}，保存构造参数供后续方法使用。
     *
     * @param menuMapper 菜单映射器依赖，保存到当前对象供后续业务方法调用
     * @param currentUserRoleService 当前用户角色服务依赖，保存到当前对象供后续业务方法调用
     */
    public EndpointAuthorizationInterceptor(
            SysMenuMapper menuMapper,
            CurrentUserRoleService currentUserRoleService) {
        this.menuMapper = menuMapper;
        this.currentUserRoleService = currentUserRoleService;
    }

    /**
     * 判断{@code pre}{@code handle}条件是否成立，供调用方选择后续分支。
     *
     * @param request 本次请求，后续经校验后用于处理{@code pre}{@code handle}
     * @param response 响应，供本方法处理{@code pre}{@code handle}时使用
     * @param handler 处理器，供本方法处理{@code pre}{@code handle}时使用
     * @return {@code pre}{@code handle}条件成立时为 true，否则为 false
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法上的策略覆盖类上的策略，避免管理控制器的类级权限
        // 把登录用户自己的运行态接口（如侧栏菜单）一并挡住。
        if (findMethodAnnotation(handlerMethod, PublicApi.class) != null
                || (findMethodAnnotation(handlerMethod, RequiresPermission.class) == null
                && findMethodAnnotation(handlerMethod, AuthenticatedApi.class) == null
                && findClassAnnotation(handlerMethod, PublicApi.class) != null)) {
            return true;
        }

        if (UserContext.getUserId() == null || UserContext.getUserId().isBlank()) {
            throw new ForbiddenException("用户未登录");
        }

        RequiresPermission permission = findMethodAnnotation(
                handlerMethod, RequiresPermission.class);
        if (permission == null
                && findMethodAnnotation(handlerMethod, AuthenticatedApi.class) == null) {
            permission = findClassAnnotation(handlerMethod, RequiresPermission.class);
        }
        if (permission != null) {
            requirePermission(permission);
            return true;
        }

        AuthenticatedApi authenticated = findMethodAnnotation(
                handlerMethod, AuthenticatedApi.class);
        if (authenticated == null) {
            authenticated = findClassAnnotation(handlerMethod, AuthenticatedApi.class);
        }
        if (authenticated != null) {
            if (isStateChanging(request) && !authenticated.objectAuthorization()) {
                throw new ForbiddenException("写接口未配置功能权限或对象级授权");
            }
            return true;
        }

        throw new ForbiddenException("接口未配置访问策略");
    }

    /**
     * 校验并获取权限；不满足约束时阻止后续处理。
     *
     * @param requirement {@code requirement}，作为 {@code Arrays.stream} 的输入影响后续处理
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
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

    /**
     * 判断是否状态{@code changing}；判断结果决定调用方的后续分支。
     *
     * @param request 本次请求，后续经校验后用于判断是否状态{@code changing}
     * @return 状态{@code changing}条件成立时为 true，否则为 false
     */
    private boolean isStateChanging(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    /**
     * 查询{@code method}{@code annotation}；查询结果供调用方展示或继续处理。
     *
     * @param handlerMethod 处理器{@code method}，作为 {@code AnnotatedElementUtils.findMergedAnnotation} 的输入影响后续处理
     * @param annotationType {@code annotation}类型标识，决定后续{@code method}{@code annotation}采用的处理分支
     * @return 符合条件的{@code a}结果，供调用方继续处理
     */
    private <A extends java.lang.annotation.Annotation> A findMethodAnnotation(
            HandlerMethod handlerMethod,
            Class<A> annotationType) {
        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), annotationType);
    }

    /**
     * 查询{@code class}{@code annotation}；查询结果供调用方展示或继续处理。
     *
     * @param handlerMethod 处理器{@code method}，作为 {@code AnnotatedElementUtils.findMergedAnnotation} 的输入影响后续处理
     * @param annotationType {@code annotation}类型标识，决定后续{@code class}{@code annotation}采用的处理分支
     * @return 符合条件的{@code a}结果，供调用方继续处理
     */
    private <A extends java.lang.annotation.Annotation> A findClassAnnotation(
            HandlerMethod handlerMethod,
            Class<A> annotationType) {
        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), annotationType);
    }
}
