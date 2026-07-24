package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.SysOrganization;
import com.workflow.service.SysOrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织部门管理控制器
 * <p>
 * 提供组织部门的树形查询、增删改、状态切换及完整路径名称查询接口。
 * </p>
 */
@RestController
@RequestMapping("/api/system/org")
@RequiredArgsConstructor
public class SysOrganizationController {
    
    /** 组织部门服务 */
    private final SysOrganizationService orgService;
    
    /**
     * 获取组织部门树
     *
     * @param type 组织类型（org-组织，dept-部门），可空
     * @return 树形结构的组织部门列表
     */
    @GetMapping("/tree")
    public Result<List<SysOrganization>> getTree(@RequestParam(required = false) String type) {
        return Result.success(orgService.getOrgTree(type));
    }
    
    /**
     * 获取所有启用中的组织部门（平铺列表）
     *
     * @return 启用中的组织部门平铺列表
     */
    @GetMapping("/enabled")
    public Result<List<SysOrganization>> getEnabledList() {
        return Result.success(orgService.getEnabledList());
    }
    
    /**
     * 根据ID查询
     *
     * @param id 组织部门ID
     * @return 组织部门对象
     */
    @GetMapping("/{id}")
    public Result<SysOrganization> getById(@PathVariable String id) {
        return Result.success(orgService.getById(id));
    }
    
    /**
     * 新增组织部门
     *
     * @param org 组织部门对象
     * @return 保存后的组织部门对象
     */
    @PostMapping
    public Result<SysOrganization> save(@RequestBody SysOrganization org) {
        return Result.success(orgService.saveOrg(org));
    }
    
    /**
     * 更新组织部门
     *
     * @param id  组织部门ID
     * @param org 组织部门对象
     * @return 更新后的组织部门对象
     */
    @PostMapping("/{id}/update")
    public Result<SysOrganization> update(@PathVariable String id, @RequestBody SysOrganization org) {
        org.setId(id);
        return Result.success(orgService.saveOrg(org));
    }
    
    /**
     * 删除组织部门
     *
     * @param id 组织部门ID
     * @return 操作结果
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable String id) {
        orgService.deleteOrg(id);
        return Result.success();
    }
    
    /**
     * 更新状态
     *
     * @param id     组织部门ID
     * @param status 状态值（可空，优先取 query 参数）
     * @param body   请求体（status 字段作为兜底）
     * @return 操作结果
     */
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, 
                                     @RequestParam(required = false) String status,
                                     @RequestBody(required = false) java.util.Map<String, String> body) {
        String finalStatus = status != null ? status : (body != null ? body.get("status") : null);
        if (finalStatus == null) {
            throw new RuntimeException("status参数不能为空");
        }
        orgService.updateStatus(id, finalStatus);
        return Result.success();
    }
    
    /**
     * 获取完整路径名称
     *
     * @param id 组织部门ID
     * @return 从顶级到当前节点的完整路径名称
     */
    @GetMapping("/{id}/path-name")
    public Result<String> getPathName(@PathVariable String id) {
        return Result.success(orgService.getFullPathName(id));
    }
}
