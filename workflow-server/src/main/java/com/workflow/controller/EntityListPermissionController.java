package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.common.UserContext;
import com.workflow.entity.EntityListPermission;
import com.workflow.entity.SysUser;
import com.workflow.service.SysUserService;
import com.workflow.service.permission.DataPermissionEngine;
import com.workflow.service.permission.EntityListPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体列表数据权限规则控制器
 */
@RestController
@RequestMapping("/api/entity-list-permission")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityListPermissionController {

    private final EntityListPermissionService permissionService;
    private final DataPermissionEngine dataPermissionEngine;
    private final SysUserService sysUserService;

    /**
     * 查询某实体的所有权限规则
     */
    @GetMapping("/entity/{entityCode}")
    public Result<List<EntityListPermission>> listByEntity(@PathVariable String entityCode) {
        return Result.success(permissionService.findByEntityCode(entityCode));
    }

    /**
     * 新增规则
     */
    @PostMapping
    public Result<EntityListPermission> save(@RequestBody EntityListPermission permission) {
        permission.setCreatedBy(UserContext.getUsername());
        permissionService.save(permission);
        return Result.success(permission);
    }

    /**
     * 更新规则
     */
    @PutMapping("/{id}")
    public Result<EntityListPermission> update(@PathVariable String id, @RequestBody EntityListPermission permission) {
        permission.setId(id);
        permissionService.updateById(permission);
        return Result.success(permission);
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        permissionService.removeById(id);
        return Result.success();
    }

    /**
     * 预览当前用户在指定实体列表下的权限 SQL
     */
    @GetMapping("/preview-sql")
    public Result<String> previewSql(@RequestParam String entityCode,
                                     @RequestParam(required = false) String listConfigId) {
        String userId = UserContext.getUserId();
        SysUser user = userId == null ? null : sysUserService.getById(userId);
        String sql = dataPermissionEngine.previewPermissionSql(entityCode, listConfigId, user);
        return Result.success(sql);
    }

    /**
     * 切换启用状态
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable String id) {
        EntityListPermission permission = permissionService.getById(id);
        if (permission != null) {
            permission.setEnabled(permission.getEnabled() != null && permission.getEnabled() == 1 ? 0 : 1);
            permissionService.updateById(permission);
        }
        return Result.success();
    }
}
