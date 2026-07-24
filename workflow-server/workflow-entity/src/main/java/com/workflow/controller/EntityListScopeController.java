package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.permission.*;
import com.workflow.entity.EntityListScopeRelease;
import com.workflow.service.CurrentUserRoleService;
import com.workflow.service.permission.EntityListScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体列表数据范围管理控制器。
 * <p>管理实体的数据范围策略（policy）与绑定（binding），并提供发布与激活接口；
 * 所有操作均要求管理员权限。
 */
@RestController
@RequestMapping("/api/entity-list-scopes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityListScopeController {

    private final EntityListScopeService scopeService;
    private final CurrentUserRoleService currentUserRoleService;

    /**
     * 查询实体的数据范围配置（策略与绑定）。GET /api/entity-list-scopes/{entityCode}
     *
     * @param entityCode 实体编码
     * @return 数据范围配置结构
     */
    @GetMapping("/{entityCode}")
    public Result<EntityListScopeConfigurationDTO> configuration(
            @PathVariable String entityCode) {
        requireAdministrator();
        return Result.success(scopeService.getConfiguration(entityCode));
    }

    /**
     * 新增数据范围策略。POST /api/entity-list-scopes/policies
     *
     * @param request 策略DTO
     * @return 保存后的策略
     */
    @PostMapping("/policies")
    public Result<EntityListScopePolicyDTO> createPolicy(
            @RequestBody EntityListScopePolicyDTO request) {
        requireAdministrator();
        return Result.success(scopeService.savePolicy(null, request));
    }

    /**
     * 更新数据范围策略。POST /api/entity-list-scopes/policies/{id}/update
     *
     * @param id      策略ID
     * @param request 策略DTO
     * @return 保存后的策略
     */
    @PostMapping("/policies/{id}/update")
    public Result<EntityListScopePolicyDTO> updatePolicy(
            @PathVariable String id,
            @RequestBody EntityListScopePolicyDTO request) {
        requireAdministrator();
        return Result.success(scopeService.savePolicy(id, request));
    }

    /**
     * 删除数据范围策略。POST /api/entity-list-scopes/policies/{id}/delete
     *
     * @param id 策略ID
     * @return 无数据返回
     */
    @PostMapping("/policies/{id}/delete")
    public Result<Void> deletePolicy(@PathVariable String id) {
        requireAdministrator();
        scopeService.deletePolicy(id);
        return Result.success();
    }

    /**
     * 新增数据范围绑定。POST /api/entity-list-scopes/bindings
     *
     * @param request 绑定DTO
     * @return 保存后的绑定
     */
    @PostMapping("/bindings")
    public Result<EntityListScopeBindingDTO> createBinding(
            @RequestBody EntityListScopeBindingDTO request) {
        requireAdministrator();
        return Result.success(scopeService.saveBinding(null, request));
    }

    /**
     * 更新数据范围绑定。POST /api/entity-list-scopes/bindings/{id}/update
     *
     * @param id      绑定ID
     * @param request 绑定DTO
     * @return 保存后的绑定
     */
    @PostMapping("/bindings/{id}/update")
    public Result<EntityListScopeBindingDTO> updateBinding(
            @PathVariable String id,
            @RequestBody EntityListScopeBindingDTO request) {
        requireAdministrator();
        return Result.success(scopeService.saveBinding(id, request));
    }

    /**
     * 删除数据范围绑定。POST /api/entity-list-scopes/bindings/{id}/delete
     *
     * @param id 绑定ID
     * @return 无数据返回
     */
    @PostMapping("/bindings/{id}/delete")
    public Result<Void> deleteBinding(@PathVariable String id) {
        requireAdministrator();
        scopeService.deleteBinding(id);
        return Result.success();
    }

    /**
     * 发布实体数据范围为新版本。POST /api/entity-list-scopes/{entityCode}/publish
     *
     * @param entityCode 实体编码
     * @param request    发布请求（可选描述）
     * @return 新建的发布记录
     */
    @PostMapping("/{entityCode}/publish")
    public Result<EntityListScopeRelease> publish(
            @PathVariable String entityCode,
            @RequestBody(required = false) EntityListScopePublishRequest request) {
        requireAdministrator();
        return Result.success(scopeService.publish(
                entityCode,
                request == null ? null : request.getDescription()));
    }

    /**
     * 激活指定版本的数据范围发布。POST /api/entity-list-scopes/{entityCode}/releases/{version}/activate
     *
     * @param entityCode 实体编码
     * @param version    发布版本号
     * @return 激活后的发布记录
     */
    @PostMapping("/{entityCode}/releases/{version}/activate")
    public Result<EntityListScopeRelease> activateRelease(
            @PathVariable String entityCode,
            @PathVariable int version) {
        requireAdministrator();
        return Result.success(scopeService.activateRelease(entityCode, version));
    }

    /** 校验当前用户为管理员，否则抛出权限异常。 */
    private void requireAdministrator() {
        currentUserRoleService.requireAdministrator("仅管理员可以配置实体列表数据范围");
    }
}
