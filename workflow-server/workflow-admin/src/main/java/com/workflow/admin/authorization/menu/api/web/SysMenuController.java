package com.workflow.admin.authorization.menu.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.menu.application.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单管理控制器
 */
@RequiresPermission("system:menu:view")
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {
    
    private final SysMenuService menuService;
    private final SysMenuMapper menuMapper;
    
    /**
     * 查询菜单树
     *
     * @return 处理后的树结果，供调用方继续处理
     */
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.success(menuService.getMenuTree());
    }

    /**
     * 分页查询指定父菜单下的直接子菜单
     *
     * @param parentId 父级ID，后续用于处理子节点时定位或关联目标
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的子节点结果，供调用方继续处理
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
     *
     * @param parentId 父级ID，后续用于处理{@code subtree}时定位或关联目标
     * @return 处理后的{@code subtree}结果，供调用方继续处理
     */
    @GetMapping("/subtree")
    public Result<List<SysMenu>> subtree(
            @RequestParam(required = false, defaultValue = "0") String parentId) {
        return Result.success(menuService.getSubtree(parentId));
    }

    /**
     * 查询当前登录用户的运行态侧栏菜单树。
     * 该方法只要求已登录：菜单管理权限 system:menu:view 属于配置后台，
     * 不能用来拦截普通用户拉取自己被授权的侧栏。
     *
     * @return 处理后的{@code sidebar}树结果，供调用方继续处理
     */
    @GetMapping("/sidebar-tree")
    @AuthenticatedApi
    public Result<List<SysMenu>> sidebarTree() {
        return Result.success(menuService.getSidebarMenuTree(UserContext.getUserId()));
    }
    
    /**
     * 根据ID查询菜单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的{@code result<sys}{@code menu>}结果，供调用方继续处理
     */
    @GetMapping("/{id}")
    public Result<SysMenu> getById(@PathVariable String id) {
        return Result.success(menuService.getById(id));
    }
    
    /**
     * 新增菜单
     *
     * @param menu 菜单，作为 {@code RequiresPermission} 的输入影响后续处理
     * @return 保存后的系统菜单结果，供调用方继续处理
     */
    @PostMapping
    @RequiresPermission("system:menu:manage")
    public Result<SysMenu> save(@Validated @RequestBody SysMenu menu) {
        return Result.success(menuService.saveMenu(menu));
    }
    
    /**
     * 更新菜单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param menu 菜单，作为 {@code RequiresPermission} 的输入影响后续处理
     * @return 更新后的系统菜单结果，供调用方继续处理
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("system:menu:manage")
    public Result<SysMenu> update(@PathVariable String id, @RequestBody SysMenu menu) {
        menu.setId(id);
        return Result.success(menuService.saveMenu(menu));
    }
    
    /**
     * 删除菜单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 删除后的系统菜单结果，供调用方继续处理
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("system:menu:manage")
    public Result<Void> delete(@PathVariable String id) {
        menuService.deleteMenu(id);
        return Result.success();
    }
    
    /**
     * 更新菜单状态
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param body 请求体，后续用于更新状态并传递处理结果
     * @return 更新后的状态结果，供调用方继续处理
     */
    @PostMapping("/{id}/status")
    @RequiresPermission("system:menu:manage")
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
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param visible 可见，供本方法更新可见时使用
     * @return 更新后的可见结果，供调用方继续处理
     */
    @PostMapping("/{id}/visible")
    @RequiresPermission("system:menu:manage")
    public Result<Void> updateVisible(@PathVariable String id, @RequestParam String visible) {
        menuService.updateVisible(id, visible);
        return Result.success();
    }
    
    /**
     * 更新菜单排序
     *
     * @param menuIds 菜单ID 集合，供本方法更新排序时使用
     * @return 更新后的排序结果，供调用方继续处理
     */
    @PostMapping("/sort")
    @RequiresPermission("system:menu:manage")
    public Result<Void> updateSort(@RequestBody List<String> menuIds) {
        menuService.updateSort(menuIds);
        return Result.success();
    }
    
    /**
     * 根据实体编码查询可用的按钮权限码集合
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的系统菜单结果，供调用方继续处理
     */
    @GetMapping("/perms")
    public Result<Set<String>> getPermsByEntityCode(@RequestParam String entityCode) {
        return Result.success(menuMapper.selectPermsByEntityCode(entityCode));
    }

    /**
     * 导出菜单
     *
     * @return 处理后的导出结果，供调用方继续处理
     */
    @GetMapping("/export")
    public Result<List<SysMenu>> export() {
        return Result.success(menuService.exportMenus());
    }
    
    /**
     * 导入菜单
     *
     * @param menus 菜单集合，供本方法处理导入菜单集合时使用
     * @return 处理后的导入菜单集合结果，供调用方继续处理
     */
    @PostMapping("/import")
    @RequiresPermission("system:menu:manage")
    public Result<Void> importMenus(@RequestBody List<SysMenu> menus) {
        menuService.importMenus(menus);
        return Result.success();
    }
    
    /**
     * 获取菜单类型选项
     *
     * @return 符合条件的系统菜单结果，供调用方继续处理
     */
    @GetMapping("/type-options")
    public Result<List<Map<String, String>>> getTypeOptions() {
        return Result.success(menuService.getMenuTypeOptions());
    }
}
