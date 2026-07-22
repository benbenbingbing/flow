package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.SysMenu;
import com.workflow.entity.SysRole;
import com.workflow.service.SysMenuService;
import com.workflow.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {
    
    private final SysRoleService roleService;
    private final SysMenuService menuService;
    
    /**
     * 查询角色列表
     */
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.getRoleList());
    }
    
    /**
     * 查询所有启用的角色
     */
    @GetMapping("/enabled")
    public Result<List<SysRole>> getEnabledRoles() {
        return Result.success(roleService.getEnabledRoles());
    }
    
    /**
     * 根据ID查询角色
     */
    @GetMapping("/{id}")
    public Result<SysRole> getById(@PathVariable String id) {
        return Result.success(roleService.getById(id));
    }
    
    /**
     * 新增角色
     */
    @PostMapping
    public Result<SysRole> save(@Validated @RequestBody SysRole role) {
        return Result.success(roleService.saveRole(role));
    }
    
    /**
     * 更新角色
     */
    @PostMapping("/{id}/update")
    public Result<SysRole> update(@PathVariable String id, @RequestBody SysRole role) {
        role.setId(id);
        return Result.success(roleService.saveRole(role));
    }
    
    /**
     * 删除角色
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable String id) {
        roleService.deleteRole(id);
        return Result.success();
    }
    
    /**
     * 更新角色状态
     */
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, 
                                     @RequestParam(required = false) String status,
                                     @RequestBody(required = false) java.util.Map<String, String> body) {
        String finalStatus = status != null ? status : (body != null ? body.get("status") : null);
        if (finalStatus == null) {
            throw new RuntimeException("status参数不能为空");
        }
        roleService.updateStatus(id, finalStatus);
        return Result.success();
    }
    
    /**
     * 获取菜单树（用于角色分配权限）
     */
    @GetMapping("/menu-tree")
    public Result<List<SysMenu>> getMenuTree() {
        return Result.success(menuService.getMenuTree());
    }
    
    /**
     * 获取角色的菜单权限
     */
    @GetMapping("/{id}/menus")
    public Result<List<SysMenu>> getRoleMenus(@PathVariable String id) {
        return Result.success(roleService.getRoleMenuTree(id));
    }
    
    /**
     * 保存角色菜单权限
     */
    @PostMapping("/{id}/menus")
    public Result<Void> saveRoleMenus(@PathVariable String id, @RequestBody List<String> menuIds) {
        roleService.saveRoleMenus(id, menuIds);
        return Result.success();
    }
}
