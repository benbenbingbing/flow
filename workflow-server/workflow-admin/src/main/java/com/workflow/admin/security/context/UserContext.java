package com.workflow.admin.security.context;

import com.workflow.core.error.ForbiddenException;
import org.springframework.util.StringUtils;

/**
 * 当前用户上下文
 * <p>
 * 基于 ThreadLocal 在请求线程内传递当前登录用户ID和用户名，
 * 由认证拦截器在请求开始时设置、请求结束时清除。
 * </p>
 */
public class UserContext {
    
    /** 当前登录用户ID（线程隔离） */
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    /** 当前登录用户名（线程隔离） */
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    /** 当前浏览器刷新会话 ID（线程隔离） */
    private static final ThreadLocal<String> SESSION_ID =
            new ThreadLocal<>();
    
    /**
     * 设置当前用户
     *
     * @param userId   用户ID
     * @param username 用户名
     */
    public static void setCurrentUser(String userId, String username) {
        setCurrentUser(userId, username, null);
    }

    /**
     * 设置当前用户和浏览器刷新会话。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param sessionId 刷新会话 ID
     */
    public static void setCurrentUser(
            String userId,
            String username,
            String sessionId) {
        USER_ID.set(userId);
        USERNAME.set(username);
        SESSION_ID.set(sessionId);
    }
    
    /**
     * 获取当前用户ID
     *
     * @return 当前用户ID，未登录返回 null
     */
    public static String getUserId() {
        return USER_ID.get();
    }
    
    /**
     * 获取当前用户名
     *
     * @return 当前用户名，未登录返回 null
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 获取当前浏览器刷新会话 ID。
     *
     * @return 当前刷新会话 ID，非浏览器上下文可为空
     */
    public static String getSessionId() {
        return SESSION_ID.get();
    }

    public static String requireUsernameOrId() {
        if (StringUtils.hasText(getUsername())) {
            return getUsername();
        }
        if (StringUtils.hasText(getUserId())) {
            return getUserId();
        }
        throw new ForbiddenException("用户未登录");
    }
    
    /**
     * 清除当前用户（清理 ThreadLocal，避免内存泄漏）
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        SESSION_ID.remove();
    }
}
