package com.workflow.admin.identity.group.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.Result;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.identity.group.application.SysGroupService;
import com.workflow.admin.identity.user.application.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户组管理控制器
 * <p>
 * 提供用户组的增删改查、状态切换、组用户保存及用户列表查询接口。
 * </p>
 */
@RequiresPermission("system:user:view")
@RestController
@RequestMapping("/api/system/group")
@RequiredArgsConstructor
public class SysGroupController {
    
    /** 用户组服务 */
    private final SysGroupService groupService;
    /** 用户服务，用于查询用户列表供选择组成员 */
    private final SysUserService userService;
    
    /**
     * 查询组列表
     *
     * @return 用户组列表
     */
    @GetMapping("/list")
    public Result<List<SysGroup>> list() {
        return Result.success(groupService.getGroupList());
    }
    
    /**
     * 查询启用的组列表
     *
     * @return 启用状态的用户组列表
     */
    @GetMapping("/enabled")
    public Result<List<SysGroup>> getEnabledGroups() {
        return Result.success(groupService.getEnabledGroups());
    }
    
    /**
     * 根据ID查询组
     *
     * @param id 组ID
     * @return 用户组对象
     */
    @GetMapping("/{id}")
    public Result<SysGroup> getById(@PathVariable String id) {
        return Result.success(groupService.getById(id));
    }
    
    /**
     * 新增组
     *
     * @param group 用户组对象
     * @return 保存后的用户组对象
     */
    @PostMapping
    @RequiresPermission("system:user:manage")
    public Result<SysGroup> save(@Validated @RequestBody SysGroup group) {
        return Result.success(groupService.saveGroup(group));
    }
    
    /**
     * 更新组
     *
     * @param id    组ID
     * @param group 用户组对象
     * @return 更新后的用户组对象
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("system:user:manage")
    public Result<SysGroup> update(@PathVariable String id, @RequestBody SysGroup group) {
        group.setId(id);
        return Result.success(groupService.saveGroup(group));
    }
    
    /**
     * 删除组
     *
     * @param id 组ID
     * @return 操作结果
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("system:user:manage")
    public Result<Void> delete(@PathVariable String id) {
        groupService.deleteGroup(id);
        return Result.success();
    }
    
    /**
     * 更新组状态
     *
     * @param id     组ID
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
        groupService.updateStatus(id, finalStatus);
        return Result.success();
    }
    
    /**
     * 保存组用户
     *
     * @param id      组ID
     * @param userIds 用户ID列表
     * @return 操作结果
     */
    @PostMapping("/{id}/users")
    @RequiresPermission("system:user:manage")
    public Result<Void> saveGroupUsers(@PathVariable String id, @RequestBody List<String> userIds) {
        groupService.saveGroupUsers(id, userIds);
        return Result.success();
    }
    
    /**
     * 获取用户列表（用于选择组成员）
     *
     * @return 用户列表
     */
    @GetMapping("/users")
    public Result<List<SysUser>> getUsers() {
        return Result.success(userService.getUserList());
    }
}
