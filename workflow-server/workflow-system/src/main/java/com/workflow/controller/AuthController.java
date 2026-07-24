package com.workflow.controller;

import com.workflow.common.JwtUtil;
import com.workflow.common.PermissionUtil;
import com.workflow.common.Result;
import com.workflow.common.UserContext;
import com.workflow.dto.LoginDTO;
import com.workflow.entity.SysUser;
import com.workflow.service.SysUserService;
import com.workflow.vo.LoginUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证控制器
 * <p>
 * 提供用户登录、获取当前登录用户信息、退出登录及权限码查询接口。
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {
    
    /** 用户服务，用于登录校验与用户信息查询 */
    private final SysUserService userService;
    /** BCrypt 密码编码器，用于登录密码校验 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 用户登录
     *
     * @param loginDTO 登录请求（用户名、密码）
     * @return 登录成功返回包含 JWT Token 的用户信息；用户不存在、被禁用或密码错误返回错误信息
     */
    @PostMapping("/login")
    public Result<LoginUserVO> login(@Validated @RequestBody LoginDTO loginDTO) {
        // 查询用户
        SysUser user = userService.getByUsername(loginDTO.getUsername());
        if (user == null) {
            return Result.error("用户名或密码错误");
        }
        
        // 检查用户状态
        if ("1".equals(user.getStatus())) {
            return Result.error("用户已被禁用");
        }
        
        // 验证密码
        // admin用户密码是admin，其他用户使用数据库存储的加密密码
        boolean passwordValid;
        if ("admin".equals(loginDTO.getUsername()) && "admin".equals(loginDTO.getPassword())) {
            passwordValid = true;
            // 更新admin用户的密码为加密后的
            if (!user.getPassword().startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode("admin"));
                userService.updatePassword(user.getId(), user.getPassword());
            }
        } else {
            passwordValid = passwordEncoder.matches(loginDTO.getPassword(), user.getPassword());
        }
        
        if (!passwordValid) {
            return Result.error("用户名或密码错误");
        }
        
        // 生成JWT Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        
        // 构建返回对象
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setToken(token);
        
        // 设置角色
        if (user.getRoles() != null) {
            vo.setRoles(user.getRoles().stream()
                    .map(r -> r.getRoleCode())
                    .collect(Collectors.toList()));
        }
        
        return Result.success(vo);
    }
    
    /**
     * 获取当前登录用户信息
     *
     * @return 当前登录用户信息；未登录或用户不存在返回错误信息
     */
    @GetMapping("/current")
    public Result<LoginUserVO> getCurrentUser() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        
        SysUser user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        
        if (user.getRoles() != null) {
            vo.setRoles(user.getRoles().stream()
                    .map(r -> r.getRoleCode())
                    .collect(Collectors.toList()));
        }
        
        return Result.success(vo);
    }
    
    /**
     * 退出登录（前端清除token即可，后端可以记录日志等）
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 可以在这里记录退出日志
        return Result.success();
    }
    
    /**
     * 获取当前登录用户的权限码集合
     *
     * @return 当前用户的权限码集合；未登录返回错误信息
     */
    @GetMapping("/permissions")
    public Result<Set<String>> getPermissions() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        return Result.success(PermissionUtil.getUserPermissions(userId));
    }
}
