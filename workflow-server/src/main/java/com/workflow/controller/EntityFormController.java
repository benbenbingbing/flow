package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.service.EntityFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体表单管理控制器
 */
@RestController
@RequestMapping("/api/entity-form")
@RequiredArgsConstructor
public class EntityFormController {
    
    private final EntityFormService formService;
    
    /**
     * 查询所有表单列表
     */
    @GetMapping("/list")
    public Result<List<EntityForm>> list() {
        return Result.success(formService.list());
    }
    
    /**
     * 查询实体的表单列表
     */
    @GetMapping("/entity/{entityId}")
    public Result<List<EntityForm>> listByEntity(@PathVariable String entityId) {
        return Result.success(formService.getFormsByEntityId(entityId));
    }
    
    /**
     * 根据ID查询表单
     */
    @GetMapping("/{id}")
    public Result<EntityForm> getById(@PathVariable String id) {
        return Result.success(formService.getById(id));
    }
    
    /**
     * 新增表单
     */
    @PostMapping
    public Result<EntityForm> save(@Validated @RequestBody EntityForm form) {
        return Result.success(formService.saveForm(form));
    }
    
    /**
     * 更新表单
     */
    @PutMapping("/{id}")
    public Result<EntityForm> update(@PathVariable String id, @RequestBody EntityForm form) {
        form.setId(id);
        return Result.success(formService.saveForm(form));
    }
    
    /**
     * 删除表单
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        formService.deleteForm(id);
        return Result.success();
    }
    
    /**
     * 获取实体的字段列表
     */
    @GetMapping("/entity/{entityId}/fields")
    public Result<List<EntityField>> getEntityFields(@PathVariable String entityId) {
        return Result.success(formService.getEntityFields(entityId));
    }
    
    /**
     * 保存表单字段
     */
    @PutMapping("/{id}/fields")
    public Result<Void> saveFormFields(@PathVariable String id, @RequestBody List<EntityFormField> fields) {
        formService.saveFormFields(id, fields);
        return Result.success();
    }
    
    /**
     * 获取表单字段
     */
    @GetMapping("/{id}/fields")
    public Result<List<EntityFormField>> getFormFields(@PathVariable String id) {
        return Result.success(formService.getFormFields(id));
    }
    
    /**
     * 获取实体的默认表单
     */
    @GetMapping("/entity/{entityId}/default")
    public Result<EntityForm> getDefaultForm(@PathVariable String entityId) {
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
        return Result.success(formService.copyForm(id));
    }
}
