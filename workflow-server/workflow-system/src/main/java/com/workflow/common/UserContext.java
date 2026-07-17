package com.workflow.common;

/**
 * 当前用户上下文
 */
public class UserContext {
    
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    
    /**
     * 设置当前用户
     */
    public static void setCurrentUser(String userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }
    
    /**
     * 获取当前用户ID
     */
    public static String getUserId() {
        return USER_ID.get();
    }
    
    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        return USERNAME.get();
    }
    
    /**
     * 清除当前用户
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
