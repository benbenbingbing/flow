package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.permission.*;
import com.workflow.entity.EntityListScopeRelease;
import com.workflow.service.CurrentUserRoleService;
import com.workflow.service.permission.EntityListScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entity-list-scopes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityListScopeController {

    private final EntityListScopeService scopeService;
    private final CurrentUserRoleService currentUserRoleService;

    @GetMapping("/{entityCode}")
    public Result<EntityListScopeConfigurationDTO> configuration(
            @PathVariable String entityCode) {
        requireAdministrator();
        return Result.success(scopeService.getConfiguration(entityCode));
    }

    @PostMapping("/policies")
    public Result<EntityListScopePolicyDTO> createPolicy(
            @RequestBody EntityListScopePolicyDTO request) {
        requireAdministrator();
        return Result.success(scopeService.savePolicy(null, request));
    }

    @PutMapping("/policies/{id}")
    public Result<EntityListScopePolicyDTO> updatePolicy(
            @PathVariable String id,
            @RequestBody EntityListScopePolicyDTO request) {
        requireAdministrator();
        return Result.success(scopeService.savePolicy(id, request));
    }

    @DeleteMapping("/policies/{id}")
    public Result<Void> deletePolicy(@PathVariable String id) {
        requireAdministrator();
        scopeService.deletePolicy(id);
        return Result.success();
    }

    @PostMapping("/bindings")
    public Result<EntityListScopeBindingDTO> createBinding(
            @RequestBody EntityListScopeBindingDTO request) {
        requireAdministrator();
        return Result.success(scopeService.saveBinding(null, request));
    }

    @PutMapping("/bindings/{id}")
    public Result<EntityListScopeBindingDTO> updateBinding(
            @PathVariable String id,
            @RequestBody EntityListScopeBindingDTO request) {
        requireAdministrator();
        return Result.success(scopeService.saveBinding(id, request));
    }

    @DeleteMapping("/bindings/{id}")
    public Result<Void> deleteBinding(@PathVariable String id) {
        requireAdministrator();
        scopeService.deleteBinding(id);
        return Result.success();
    }

    @PostMapping("/{entityCode}/publish")
    public Result<EntityListScopeRelease> publish(
            @PathVariable String entityCode,
            @RequestBody(required = false) EntityListScopePublishRequest request) {
        requireAdministrator();
        return Result.success(scopeService.publish(
                entityCode,
                request == null ? null : request.getDescription()));
    }

    @PostMapping("/{entityCode}/releases/{version}/activate")
    public Result<EntityListScopeRelease> activateRelease(
            @PathVariable String entityCode,
            @PathVariable int version) {
        requireAdministrator();
        return Result.success(scopeService.activateRelease(entityCode, version));
    }

    private void requireAdministrator() {
        currentUserRoleService.requireAdministrator("仅管理员可以配置实体列表数据范围");
    }
}
