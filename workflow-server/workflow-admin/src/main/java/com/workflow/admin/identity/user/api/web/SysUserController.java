package com.workflow.admin.identity.user.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.authorization.role.application.SysRoleService;
import com.workflow.admin.identity.user.application.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 * <p>
 * 提供用户的增删改查、状态切换、密码重置及角色列表查询接口。
 * </p>
 */
@RequiresPermission("system:user:view")
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {
    
    /** 用户服务 */
    private final SysUserService userService;
    /** 角色服务，用于查询启用角色供分配 */
    private final SysRoleService roleService;
    
    /**
     * 查询用户列表
     *
     * @return 用户列表
     */
    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        return Result.success(userService.getUserList());
    }

    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orgId,
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) String roleId) {
        return Result.success(userService.getUserPage(
                pageNum, pageSize, keyword, status, orgId, deptId, roleId));
    }
    
    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户对象
     */
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable String id) {
        return Result.success(userService.getById(id));
    }
    
    /**
     * 新增用户
     *
     * @param user 用户对象
     * @return 保存后的用户对象
     */
    @PostMapping
    @RequiresPermission("system:user:manage")
    public Result<SysUser> save(@Validated @RequestBody SysUser user) {
        return Result.success(userService.saveUser(user));
    }
    
    /**
     * 更新用户
     *
     * @param id   用户ID
     * @param user 用户对象
     * @return 更新后的用户对象
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("system:user:manage")
    public Result<SysUser> update(@PathVariable String id, @RequestBody SysUser user) {
        user.setId(id);
        return Result.success(userService.saveUser(user));
    }
    
    /**
     * 删除用户
     *
     * @param id 用户ID
     * @return 操作结果
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("system:user:manage")
    public Result<Void> delete(@PathVariable String id) {
        userService.deleteUser(id);
        return Result.success();
    }
    
    /**
     * 更新用户状态
     *
     * @param id     用户ID
     * @param status 状态值（可空，优先取 query 参数）
     * @param body   请求体（status 字段作为兜底）
     * @return 操作结果
     */
    @PostMapping("/{id}/status")
    @RequiresPermission("system:user:manage")
    public Result<Void> updateStatus(@PathVariable String id, 
                                     @RequestParam(required = false) String status,
                                     @RequestBody(required = false) java.util.Map<String, String> body) {
        String finalStatus = status != null ? status : (body != null ? body.get("status") : null);
        if (finalStatus == null) {
            throw new RuntimeException("status参数不能为空");
        }
        userService.updateStatus(id, finalStatus);
        return Result.success();
    }

    @PostMapping("/batch/status")
    @RequiresPermission("system:user:manage")
    public Result<Void> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        Object requestedStatus = body.get("status");
        if (requestedStatus == null) {
            throw new IllegalArgumentException("status参数不能为空");
        }
        userService.batchUpdateStatus(stringList(body.get("userIds")), String.valueOf(requestedStatus));
        return Result.success();
    }

    @PostMapping("/batch/roles")
    @RequiresPermission("system:user:manage")
    public Result<Void> batchAssignRoles(@RequestBody Map<String, Object> body) {
        userService.batchAssignRoles(stringList(body.get("userIds")), stringList(body.get("roleIds")));
        return Result.success();
    }
    
    /**
     * 重置密码
     *
     * @param id 用户ID
     * @return 操作结果
     */
    @PostMapping("/{id}/reset-password")
    @RequiresPermission("system:user:reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable String id) {
        return Result.success(Map.of(
                "temporaryPassword", userService.resetPassword(id),
                "passwordResetRequired", true));
    }
    
    /**
     * 获取角色列表（用于用户分配角色）
     *
     * @return 启用状态的角色列表
     */
    @GetMapping("/roles")
    public Result<List<SysRole>> getRoles() {
        return Result.success(roleService.getEnabledRoles());
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
