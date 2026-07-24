package com.workflow.config;

import com.workflow.common.JwtUtil;
import com.workflow.common.UserContext;
import com.workflow.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    /** JSON 序列化器，用于写出错误响应 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
        // 登录、退出接口放行
        String uri = request.getRequestURI();
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/logout")) {
            return true;
        }
        
        // 获取Token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // 验证Token
        if (token == null || !JwtUtil.validateToken(token)) {
            writeErrorResponse(response, 401, "未登录或登录已过期");
            return false;
        }
        
        // 设置当前用户上下文
        String userId = JwtUtil.getUserIdFromToken(token);
        String username = JwtUtil.getUsernameFromToken(token);
        UserContext.setCurrentUser(userId, username);
        
        // 设置 request attribute，供控制器使用
        request.setAttribute("userId", userId);
        request.setAttribute("userName", username);
        
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
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
