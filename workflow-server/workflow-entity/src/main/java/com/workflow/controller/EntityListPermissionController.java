package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.common.UserContext;
import com.workflow.dto.permission.EntityListPermissionSaveRequest;
import com.workflow.dto.permission.PermissionPreviewDTO;
import com.workflow.entity.EntityListPermission;
import com.workflow.entity.SysUser;
import com.workflow.service.CurrentUserRoleService;
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
    private final CurrentUserRoleService currentUserRoleService;

    /**
     * 查询某实体的所有权限规则
     */
    @GetMapping("/entity/{entityCode}")
    public Result<List<EntityListPermission>> listByEntity(@PathVariable String entityCode) {
        requireAdministrator();
        return Result.success(permissionService.findByEntityCode(entityCode));
    }

    /**
     * 新增规则
     */
    @PostMapping
    public Result<EntityListPermission> save(
            @RequestBody EntityListPermissionSaveRequest request) {
        requireAdministrator();
        return Result.success(permissionService.create(
                request,
                UserContext.getUsername()));
    }

    /**
     * 更新规则
     */
    @PutMapping("/{id}")
    public Result<EntityListPermission> update(
            @PathVariable String id,
            @RequestBody EntityListPermissionSaveRequest request) {
        requireAdministrator();
        return Result.success(permissionService.update(id, request));
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        requireAdministrator();
        permissionService.removeById(id);
        return Result.success();
    }

    /**
     * 预览当前用户在指定实体列表下的权限 SQL
     */
    @GetMapping("/preview-sql")
    public Result<PermissionPreviewDTO> previewSql(@RequestParam String entityCode,
                                                   @RequestParam(required = false) String listConfigId) {
        requireAdministrator();
        String userId = UserContext.getUserId();
        SysUser user = userId == null ? null : sysUserService.getById(userId);
        PermissionPreviewDTO preview = dataPermissionEngine.previewPermissionDetail(entityCode, listConfigId, user);
        return Result.success(preview);
    }

    /**
     * 预览某条规则生成的权限 SQL（不考虑其它规则与优先级编排）。
     */
    @GetMapping("/{id}/preview-sql")
    public Result<PermissionPreviewDTO> previewRuleSql(@PathVariable String id) {
        requireAdministrator();
        EntityListPermission rule = permissionService.getById(id);
        if (rule == null) {
            throw new RuntimeException("规则不存在");
        }
        String userId = UserContext.getUserId();
        SysUser user = userId == null ? null : sysUserService.getById(userId);
        PermissionPreviewDTO preview = dataPermissionEngine.previewRuleSql(rule, user);
        return Result.success(preview);
    }

    /**
     * 切换启用状态
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable String id) {
        requireAdministrator();
        permissionService.toggleEnabled(id);
        return Result.success();
    }

    private void requireAdministrator() {
        currentUserRoleService.requireAdministrator("仅管理员可以配置实体列表数据权限");
    }
}
