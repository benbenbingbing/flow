package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.SysGroup;
import com.workflow.entity.SysUser;
import com.workflow.service.SysGroupService;
import com.workflow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户组管理控制器
 */
@RestController
@RequestMapping("/api/system/group")
@RequiredArgsConstructor
public class SysGroupController {
    
    private final SysGroupService groupService;
    private final SysUserService userService;
    
    /**
     * 查询组列表
     */
    @GetMapping("/list")
    public Result<List<SysGroup>> list() {
        return Result.success(groupService.getGroupList());
    }
    
    /**
     * 查询启用的组列表
     */
    @GetMapping("/enabled")
    public Result<List<SysGroup>> getEnabledGroups() {
        return Result.success(groupService.getEnabledGroups());
    }
    
    /**
     * 根据ID查询组
     */
    @GetMapping("/{id}")
    public Result<SysGroup> getById(@PathVariable String id) {
        return Result.success(groupService.getById(id));
    }
    
    /**
     * 新增组
     */
    @PostMapping
    public Result<SysGroup> save(@Validated @RequestBody SysGroup group) {
        return Result.success(groupService.saveGroup(group));
    }
    
    /**
     * 更新组
     */
    @PostMapping("/{id}/update")
    public Result<SysGroup> update(@PathVariable String id, @RequestBody SysGroup group) {
        group.setId(id);
        return Result.success(groupService.saveGroup(group));
    }
    
    /**
     * 删除组
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable String id) {
        groupService.deleteGroup(id);
        return Result.success();
    }
    
    /**
     * 更新组状态
     */
    @PostMapping("/{id}/status")
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
     */
    @PostMapping("/{id}/users")
    public Result<Void> saveGroupUsers(@PathVariable String id, @RequestBody List<String> userIds) {
        groupService.saveGroupUsers(id, userIds);
        return Result.success();
    }
    
    /**
     * 获取用户列表（用于选择组成员）
     */
    @GetMapping("/users")
    public Result<List<SysUser>> getUsers() {
        return Result.success(userService.getUserList());
    }
}
