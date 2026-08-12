package com.workflow.admin.auth.infrastructure;

import com.workflow.admin.auth.application.AuthSessionException;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.application.AuthenticatedAccess;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT认证拦截器
 * <p>
 * 拦截请求校验 JWT Token，校验通过后将用户信息写入 {@link UserContext} 与 request attribute；
 * 登录、退出接口直接放行。请求结束后清理 ThreadLocal，避免内存泄漏。
 * </p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    /** JSON 序列化器，用于写出错误响应 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Access Token 与服务端刷新会话的联合认证服务。 */
    private final AuthSessionService authSessionService;

    /**
     * MVC 切片测试不会加载系统服务，使用 Provider 可让认证拦截器正常创建；
     * 完整应用中仍必须存在 AuthSessionService，否则受保护请求按不可用处理。
     */
    @Autowired
    public AuthInterceptor(
            ObjectProvider<AuthSessionService>
                    authSessionServiceProvider) {
        this.authSessionService =
                authSessionServiceProvider.getIfAvailable();
    }

    public AuthInterceptor(
            AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }
    
    /**
     * 请求前置处理：校验 Token 并设置当前用户上下文
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return 校验通过返回 true 放行；未登录或 Token 失效返回 false 并写出 401 响应
     * @throws Exception 写出响应发生 IO 异常时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 登录、刷新和退出端点可在 Access Token 缺失或过期时处理。
        String uri = request.getRequestURI();
        if (uri.equals("/api/auth/login")
                || uri.equals("/api/auth/refresh")
                || uri.equals("/api/auth/logout")) {
            return true;
        }
        
        // 获取Token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        if (authSessionService == null) {
            writeErrorResponse(response, 503, "认证服务暂不可用");
            return false;
        }
        AuthenticatedAccess authenticated;
        try {
            authenticated =
                    authSessionService.authenticateAccess(token);
        } catch (AuthSessionException exception) {
            writeErrorResponse(
                    response,
                    401,
                    exception.getErrorCode(),
                    exception.getMessage());
            return false;
        }
        UserContext.setCurrentUser(
                authenticated.userId(),
                authenticated.username(),
                authenticated.sessionId());
        if (authenticated.passwordResetRequired()
                && !uri.equals("/api/auth/current")
                && !uri.equals("/api/auth/change-password")
                && !uri.equals("/api/auth/logout")) {
            writeErrorResponse(response, 428, "首次登录或密码重置后，请先修改密码");
            UserContext.clear();
            return false;
        }
        
        // 设置 request attribute，供控制器使用
        request.setAttribute(
                "userId",
                authenticated.userId());
        request.setAttribute(
                "userName",
                authenticated.username());
        request.setAttribute(
                "sessionId",
                authenticated.sessionId());
        
        return true;
    }
    
    /**
     * 请求完成后的回调：清理当前用户上下文
     *
     * @param request   HTTP 请求
     * @param response  HTTP 响应
     * @param handler   处理器
     * @param ex        处理过程中抛出的异常（可为空）
     * @throws Exception 清理过程发生异常时抛出
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除用户上下文
        UserContext.clear();
    }
    
    /**
     * 写出 JSON 格式的错误响应
     *
     * @param response HTTP 响应
     * @param code     HTTP 状态码/业务码
     * @param message  错误提示信息
     * @throws IOException 写出响应发生 IO 异常时抛出
     */
    private void writeErrorResponse(HttpServletResponse response, int code, String message) throws IOException {
        writeErrorResponse(response, code, null, message);
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            int code,
            String errorCode,
            String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        Result<Void> result = errorCode == null
                ? Result.error(code, message)
                : Result.error(
                        code,
                        errorCode,
                        message);
        response.getWriter().write(
                objectMapper.writeValueAsString(result));
    }
}
