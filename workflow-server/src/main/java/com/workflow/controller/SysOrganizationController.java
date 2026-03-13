package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.SysOrganization;
import com.workflow.service.SysOrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织部门管理控制器
 */
@RestController
@RequestMapping("/api/system/org")
@RequiredArgsConstructor
public class SysOrganizationController {
    
    private final SysOrganizationService orgService;
    
    /**
     * 获取组织部门树
     */
    @GetMapping("/tree")
    public Result<List<SysOrganization>> getTree(@RequestParam(required = false) String type) {
        return Result.success(orgService.getOrgTree(type));
    }
    
    /**
     * 获取所有启用中的组织部门（平铺列表）
     */
    @GetMapping("/enabled")
    public Result<List<SysOrganization>> getEnabledList() {
        return Result.success(orgService.getEnabledList());
    }
    
    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public Result<SysOrganization> getById(@PathVariable String id) {
        return Result.success(orgService.getById(id));
    }
    
    /**
     * 新增组织部门
     */
    @PostMapping
    public Result<SysOrganization> save(@RequestBody SysOrganization org) {
        return Result.success(orgService.saveOrg(org));
    }
    
    /**
     * 更新组织部门
     */
    @PutMapping("/{id}")
    public Result<SysOrganization> update(@PathVariable String id, @RequestBody SysOrganization org) {
        org.setId(id);
        return Result.success(orgService.saveOrg(org));
    }
    
    /**
     * 删除组织部门
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        orgService.deleteOrg(id);
        return Result.success();
    }
    
    /**
     * 更新状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam String status) {
        orgService.updateStatus(id, status);
        return Result.success();
    }
    
    /**
     * 获取完整路径名称
     */
    @GetMapping("/{id}/path-name")
    public Result<String> getPathName(@PathVariable String id) {
        return Result.success(orgService.getFullPathName(id));
    }
}
