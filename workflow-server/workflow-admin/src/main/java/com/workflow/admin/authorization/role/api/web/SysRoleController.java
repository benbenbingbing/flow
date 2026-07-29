package com.workflow.admin.authorization.role.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.authorization.menu.application.SysMenuService;
import com.workflow.admin.authorization.role.application.SysRoleService;
import com.workflow.admin.identity.user.application.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 * <p>
 * 提供角色的增删改查、状态切换及角色菜单权限的查询与保存接口。
 * </p>
 */
@RequiresPermission("system:role:view")
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {
    
    /** 角色服务 */
    private final SysRoleService roleService;
    /** 菜单服务，用于获取菜单树供角色分配权限 */
    private final SysMenuService menuService;
    /** 用户服务，用于查询角色成员 */
    private final SysUserService userService;
    
    /**
     * 查询角色列表
     *
     * @return 角色列表
     */
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.getRoleList());
    }
    
    /**
     * 查询所有启用的角色
     *
     * @return 启用状态的角色列表
     */
    @GetMapping("/enabled")
    public Result<List<SysRole>> getEnabledRoles() {
        return Result.success(roleService.getEnabledRoles());
    }
    
    /**
     * 根据ID查询角色
     *
     * @param id 角色ID
     * @return 角色对象
     */
    @GetMapping("/{id}")
    public Result<SysRole> getById(@PathVariable String id) {
        return Result.success(roleService.getById(id));
    }
    
    /**
     * 新增角色
     *
     * @param role 角色对象
     * @return 保存后的角色对象
     */
    @PostMapping
    @RequiresPermission("system:role:manage")
    public Result<SysRole> save(@Validated @RequestBody SysRole role) {
        return Result.success(roleService.saveRole(role));
    }
    
    /**
     * 更新角色
     *
     * @param id   角色ID
     * @param role 角色对象
     * @return 更新后的角色对象
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("system:role:manage")
    public Result<SysRole> update(@PathVariable String id, @RequestBody SysRole role) {
        role.setId(id);
        return Result.success(roleService.saveRole(role));
    }
    
    /**
     * 删除角色
     *
     * @param id 角色ID
     * @return 操作结果
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("system:role:manage")
    public Result<Void> delete(@PathVariable String id) {
        roleService.deleteRole(id);
        return Result.success();
    }
    
    /**
     * 更新角色状态
     *
     * @param id     角色ID
     * @param status 状态值（可空，优先取 query 参数）
     * @param body   请求体（status 字段作为兜底）
     * @return 操作结果
     */
    @PostMapping("/{id}/status")
    @RequiresPermission("system:role:manage")
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
     *
     * @return 菜单树
     */
    @GetMapping("/menu-tree")
    public Result<List<SysMenu>> getMenuTree() {
        return Result.success(menuService.getMenuTree());
    }
    
    /**
     * 获取角色的菜单权限
     *
     * @param id 角色ID
     * @return 角色拥有的菜单权限树
     */
    @GetMapping("/{id}/menus")
    public Result<List<SysMenu>> getRoleMenus(@PathVariable String id) {
        return Result.success(roleService.getRoleMenuTree(id));
    }

    /**
     * 分页查询已分配当前角色的用户
     *
     * @param id       角色ID
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  用户名、昵称、邮箱或手机号关键字
     * @return 用户分页结果
     */
    @GetMapping("/{id}/users")
    public Result<PageResult<SysUser>> getRoleUsers(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(userService.getUsersByRolePage(id, pageNum, pageSize, keyword));
    }
    
    /**
     * 保存角色菜单权限
     *
     * @param id      角色ID
     * @param menuIds 菜单ID列表
     * @return 操作结果
     */
    @PostMapping("/{id}/menus")
    @RequiresPermission("system:role:manage")
    public Result<Void> saveRoleMenus(@PathVariable String id, @RequestBody List<String> menuIds) {
        roleService.saveRoleMenus(id, menuIds);
        return Result.success();
    }
}
