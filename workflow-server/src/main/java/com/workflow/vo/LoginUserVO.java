package com.workflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录用户信息VO
 */
@Data
public class LoginUserVO {
    
    /**
     * 用户ID
     */
    private String id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像
     */
    private String avatar;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 角色列表
     */
    private List<String> roles;
    
    /**
     * JWT Token
     */
    private String token;
}
