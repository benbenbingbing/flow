package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.dto.EntityFormMetadataPatchRequest;
import com.workflow.dto.EntityFormSaveRequest;
import com.workflow.service.EntityFormService;
import com.workflow.service.UiConfigDraftMetadataService;
import com.workflow.service.UiConfigurationAccessService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 实体表单管理控制器
 */
@RestController
@RequestMapping("/api/entity-form")
@RequiredArgsConstructor
public class EntityFormController {
    
    private final EntityFormService formService;
    private final UiConfigDraftMetadataService metadataService;
    private final UiConfigurationAccessService accessService;
    
    /**
     * 查询所有表单列表
     */
    @GetMapping("/list")
    public Result<List<EntityForm>> list() {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(formService.list());
    }
    
    /**
     * 查询实体的表单列表
     */
    @GetMapping("/entity/{entityId}")
    public Result<List<EntityForm>> listByEntity(@PathVariable String entityId) {
        accessService.requireEntityFormAccess(entityId);
        return Result.success(formService.getFormsByEntityId(entityId));
    }
    
    /**
     * 根据ID查询表单
     */
    @GetMapping("/{id}")
    public Result<EntityForm> getById(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(formService.getById(id));
    }
    
    /**
     * 新增表单
     */
    @PostMapping
    public Result<EntityForm> save(@Validated @RequestBody EntityForm form) {
        if (StringUtils.hasText(form.getId())) {
            throw new IllegalArgumentException("新增表单不能携带 id");
        }
        accessService.requireNewFormAccess(form);
        return Result.success(formService.saveForm(form));
    }
    
    /**
     * 更新表单
     */
    @PutMapping("/{id}")
    public Result<EntityForm> update(
            @PathVariable String id,
            @RequestBody EntityFormSaveRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(formService.saveForm(
                request.toEntity(id),
                request.getExpectedRevision()));
    }

    @PatchMapping("/{id}")
    public Result<EntityForm> patch(
            @PathVariable String id,
            @RequestBody EntityFormMetadataPatchRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(metadataService.patchForm(id, request));
    }
    
    /**
     * 删除表单
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        accessService.requireFormAccess(id);
        formService.deleteForm(id);
        return Result.success();
    }
    
    /**
     * 获取实体的字段列表
     */
    @GetMapping("/entity/{entityId}/fields")
    public Result<List<EntityField>> getEntityFields(@PathVariable String entityId) {
        accessService.requireEntityFormAccess(entityId);
        return Result.success(formService.getEntityFields(entityId));
    }
    
    /**
     * 保存表单字段
     */
    @PutMapping("/{id}/fields")
    public Result<Void> saveFormFields(
            @PathVariable String id,
            @RequestParam Integer expectedRevision,
            @RequestBody List<EntityFormField> fields) {
        accessService.requireFormAccess(id);
        formService.saveFormFields(id, fields, expectedRevision);
        return Result.success();
    }
    
    /**
     * 获取表单字段
     */
    @GetMapping("/{id}/fields")
    public Result<List<EntityFormField>> getFormFields(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(formService.getFormFields(id));
    }
    
    /**
     * 获取实体的默认表单
     */
    @GetMapping("/entity/{entityId}/default")
    public Result<EntityForm> getDefaultForm(@PathVariable String entityId) {
        accessService.requireEntityFormAccess(entityId);
        EntityForm form = formService.getDefaultForm(entityId);
        if (form == null) {
            return Result.success(null);
        }
        return Result.success(form);
    }
    
    /**
     * 复制表单
     */
    @PostMapping("/{id}/copy")
    public Result<EntityForm> copyForm(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(formService.copyForm(id));
    }
    
    /**
     * 设置默认表单
     */
    @PutMapping("/{id}/default")
    public Result<Void> setDefaultForm(@PathVariable String id) {
        accessService.requireFormAccess(id);
        formService.setDefaultForm(id);
        return Result.success();
    }
    
    /**
     * 仅更新表单初始化配置
     */
    @PutMapping("/{id}/init-config")
    public Result<Void> updateInitConfig(@PathVariable String id, @RequestBody InitConfigRequest request) {
        accessService.requireFormAccess(id);
        formService.updateInitConfig(id, request.getInitConfig());
        return Result.success();
    }
    
    @Data
    public static class InitConfigRequest {
        private Map<String, Object> initConfig;
    }
}
