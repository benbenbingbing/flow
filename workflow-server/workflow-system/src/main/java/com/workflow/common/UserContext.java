package com.workflow.common;

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
    
    /**
     * 设置当前用户
     *
     * @param userId   用户ID
     * @param username 用户名
     */
    public static void setCurrentUser(String userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
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
     * 清除当前用户（清理 ThreadLocal，避免内存泄漏）
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
