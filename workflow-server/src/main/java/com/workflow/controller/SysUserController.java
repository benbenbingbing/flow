package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysUser;
import com.workflow.service.SysRoleService;
import com.workflow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {
    
    private final SysUserService userService;
    private final SysRoleService roleService;
    
    /**
     * 查询用户列表
     */
    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        return Result.success(userService.getUserList());
    }
    
    /**
     * 根据ID查询用户
     */
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable String id) {
        return Result.success(userService.getById(id));
    }
    
    /**
     * 新增用户
     */
    @PostMapping
    public Result<SysUser> save(@Validated @RequestBody SysUser user) {
        return Result.success(userService.saveUser(user));
    }
    
    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable String id, @RequestBody SysUser user) {
        user.setId(id);
        return Result.success(userService.saveUser(user));
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        userService.deleteUser(id);
        return Result.success();
    }
    
    /**
     * 更新用户状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam String status) {
        userService.updateStatus(id, status);
        return Result.success();
    }
    
    /**
     * 重置密码
     */
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable String id) {
        userService.resetPassword(id);
        return Result.success();
    }
    
    /**
     * 获取角色列表（用于用户分配角色）
     */
    @GetMapping("/roles")
    public Result<List<SysRole>> getRoles() {
        return Result.success(roleService.getEnabledRoles());
    }
}
