package com.workflow.config;

import com.workflow.common.JwtUtil;
import com.workflow.common.UserContext;
import com.workflow.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT认证拦截器
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 登录接口放行
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/")) {
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
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除用户上下文
        UserContext.clear();
    }
    
    private void writeErrorResponse(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
