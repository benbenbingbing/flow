package com.workflow.admin.identity.user.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.identity.user.api.request.ResetPasswordDTO;
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

    /**
     * 分页查询系统用户；查询结果供调用方展示或继续处理。
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，作为 {@code Result.success} 的输入影响后续处理
     * @param status 状态标识，决定后续系统用户采用的处理分支
     * @param orgId 组织ID，后续用于分页查询系统用户时定位或关联目标
     * @param deptId 部门ID，后续用于分页查询系统用户时定位或关联目标
     * @param roleId 角色ID，后续用于分页查询系统用户时定位或关联目标
     * @param positionCode 位置编码，后续用于分页查询系统用户时定位或关联目标
     * @return 符合条件的系统用户结果，供调用方继续处理
     */
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orgId,
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) String roleId,
            @RequestParam(required = false) String positionCode) {
        return Result.success(userService.getUserPage(
                pageNum, pageSize, keyword, status, orgId, deptId, roleId,
                positionCode));
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

    /**
     * 处理批次更新状态，并将结果传给后续步骤。
     *
     * @param body 请求体，后续用于处理批次更新状态并传递处理结果
     * @return 处理后的批次更新状态结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理批次{@code assign}角色集合，并将结果传给后续步骤。
     *
     * @param body 请求体，后续用于处理批次{@code assign}角色集合并传递处理结果
     * @return 处理后的批次{@code assign}角色集合结果，供调用方继续处理
     */
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
     * @param request 本次请求，后续经校验后用于处理重置密码
     * @return 操作结果
     */
    @PostMapping("/{id}/reset-password")
    @RequiresPermission("system:user:reset-password")
    public Result<Void> resetPassword(
            @PathVariable String id,
            @Validated @RequestBody ResetPasswordDTO request) {
        userService.resetPassword(id, request.getNewPassword());
        return Result.success();
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

    /**
     * 整理字符串列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串列表的原始输入，结果供调用方继续使用
     * @return 系统用户集合，供调用方遍历或展示
     */
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
