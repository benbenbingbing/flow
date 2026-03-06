package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.SysMenu;
import com.workflow.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 菜单管理控制器
 */
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {
    
    private final SysMenuService menuService;
    
    /**
     * 查询菜单树
     */
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.success(menuService.getMenuTree());
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
    @PutMapping("/{id}")
    public Result<SysMenu> update(@PathVariable String id, @RequestBody SysMenu menu) {
        menu.setId(id);
        return Result.success(menuService.saveMenu(menu));
    }
    
    /**
     * 删除菜单
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        menuService.deleteMenu(id);
        return Result.success();
    }
    
    /**
     * 更新菜单状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam String status) {
        menuService.updateStatus(id, status);
        return Result.success();
    }
    
    /**
     * 更新菜单显示状态
     */
    @PutMapping("/{id}/visible")
    public Result<Void> updateVisible(@PathVariable String id, @RequestParam String visible) {
        menuService.updateVisible(id, visible);
        return Result.success();
    }
    
    /**
     * 更新菜单排序
     */
    @PutMapping("/sort")
    public Result<Void> updateSort(@RequestBody List<String> menuIds) {
        menuService.updateSort(menuIds);
        return Result.success();
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
