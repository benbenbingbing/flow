package com.workflow.controller;

import com.workflow.common.PageResult;
import com.workflow.common.Result;
import com.workflow.dto.permission.EntityPermissionOptionDTO;
import com.workflow.entity.SysMenu;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.service.SysMenuService;
import com.workflow.service.permission.EntityPermissionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单管理控制器
 */
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {
    
    private final SysMenuService menuService;
    private final SysMenuMapper menuMapper;
    private final EntityPermissionCatalogService entityPermissionCatalogService;
    
    /**
     * 查询菜单树
     */
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.success(menuService.getMenuTree());
    }

    /**
     * 分页查询指定父菜单下的直接子菜单
     */
    @GetMapping("/children")
    public Result<PageResult<SysMenu>> children(
            @RequestParam(required = false, defaultValue = "0") String parentId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(menuService.getChildrenPage(parentId, pageNum, pageSize));
    }

    /**
     * 查询以指定节点为根的完整子树（展开时一次性加载所有后代）
     */
    @GetMapping("/subtree")
    public Result<List<SysMenu>> subtree(
            @RequestParam(required = false, defaultValue = "0") String parentId) {
        return Result.success(menuService.getSubtree(parentId));
    }

    /**
     * 查询运行态侧栏菜单树
     */
    @GetMapping("/sidebar-tree")
    public Result<List<SysMenu>> sidebarTree() {
        return Result.success(menuService.getSidebarMenuTree());
    }
    
    /**
     * 根据ID查询菜单
     */
    @GetMapping("/{id}")
    public Result<SysMenu> getById(@PathVariable String id) {
        return Result.success(menuService.getById(id));
    }
    
    /**
     * 新增菜单
     */
    @PostMapping
    public Result<SysMenu> save(@Validated @RequestBody SysMenu menu) {
        return Result.success(menuService.saveMenu(menu));
    }
    
    /**
     * 更新菜单
     */
    @PostMapping("/{id}/update")
    public Result<SysMenu> update(@PathVariable String id, @RequestBody SysMenu menu) {
        menu.setId(id);
        return Result.success(menuService.saveMenu(menu));
    }
    
    /**
     * 删除菜单
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable String id) {
        menuService.deleteMenu(id);
        return Result.success();
    }
    
    /**
     * 更新菜单状态
     */
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, 
                                     @RequestParam(required = false) String status,
                                     @RequestBody(required = false) java.util.Map<String, String> body) {
        String finalStatus = status != null ? status : (body != null ? body.get("status") : null);
        if (finalStatus == null) {
            throw new RuntimeException("status参数不能为空");
        }
        menuService.updateStatus(id, finalStatus);
        return Result.success();
    }
    
    /**
     * 更新菜单显示状态
     */
    @PostMapping("/{id}/visible")
    public Result<Void> updateVisible(@PathVariable String id, @RequestParam String visible) {
        menuService.updateVisible(id, visible);
        return Result.success();
    }
    
    /**
     * 更新菜单排序
     */
    @PostMapping("/sort")
    public Result<Void> updateSort(@RequestBody List<String> menuIds) {
        menuService.updateSort(menuIds);
        return Result.success();
    }
    
    /**
     * 根据实体编码查询可用的按钮权限码集合
     */
    @GetMapping("/perms")
    public Result<Set<String>> getPermsByEntityCode(@RequestParam String entityCode) {
        return Result.success(menuMapper.selectPermsByEntityCode(entityCode));
    }

    @GetMapping("/entity-permission-options")
    public Result<List<EntityPermissionOptionDTO>> getEntityPermissionOptions(@RequestParam String entityCode) {
        return Result.success(entityPermissionCatalogService.getOptions(entityCode));
    }
    
    /**
     * 导出菜单
     */
    @GetMapping("/export")
    public Result<List<SysMenu>> export() {
        return Result.success(menuService.exportMenus());
    }
    
    /**
     * 导入菜单
     */
    @PostMapping("/import")
    public Result<Void> importMenus(@RequestBody List<SysMenu> menus) {
        menuService.importMenus(menus);
        return Result.success();
    }
    
    /**
     * 获取菜单类型选项
     */
    @GetMapping("/type-options")
    public Result<List<Map<String, String>>> getTypeOptions() {
        return Result.success(menuService.getMenuTypeOptions());
    }
}
