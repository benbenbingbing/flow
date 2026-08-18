package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.Result;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体状态控制器
 */
@RequiresPermission("entity:definition:view")
@RestController
@RequestMapping("/api/entity-status")
@RequiredArgsConstructor
public class EntityStatusController {
    
    private final EntityStatusService entityStatusService;
    private final EntityActionCapabilityService actionCapabilityService;
    
    /**
     * 查询实体的状态列表。
     * 运行态列表/表单按编码读取状态选项，不能要求 entity:definition:view。
     * 登录用户需具备该实体的设计查看权，或任一标准数据动作权限。
     */
    @GetMapping("/list/{entityCode}")
    @AuthenticatedApi(objectAuthorization = true)
    public Result<List<EntityStatus>> listByEntityCode(@PathVariable String entityCode) {
        actionCapabilityService.requireEntityMetadataAccess(entityCode);
        List<EntityStatus> list = entityStatusService.findByEntityCode(entityCode);
        return Result.success(list);
    }
    
    /**
     * 按分类查询实体状态。
     * 与按编码列表相同，属于运行态元数据读取。
     */
    @GetMapping("/list/{entityCode}/{category}")
    @AuthenticatedApi(objectAuthorization = true)
    public Result<List<EntityStatus>> listByCategory(@PathVariable String entityCode, @PathVariable String category) {
        actionCapabilityService.requireEntityMetadataAccess(entityCode);
        List<EntityStatus> list = entityStatusService.findByCategory(entityCode, category);
        return Result.success(list);
    }
    
    /**
     * 保存实体状态
     */
    @PostMapping("/save")
    @RequiresPermission("entity:definition:manage")
    public Result<Void> save(@RequestBody EntityStatus status) {
        entityStatusService.saveStatus(status);
        return Result.success();
    }
    
    /**
     * 批量保存实体状态
     */
    @PostMapping("/save-list/{entityCode}")
    @RequiresPermission("entity:definition:manage")
    public Result<Void> saveList(@PathVariable String entityCode, @RequestBody List<EntityStatus> statuses) {
        entityStatusService.saveStatusList(entityCode, statuses);
        return Result.success();
    }
    
    /**
     * 删除实体状态
     */
    @PostMapping("/delete/{id}")
    @RequiresPermission("entity:definition:manage")
    public Result<Void> delete(@PathVariable String id) {
        entityStatusService.deleteStatus(id);
        return Result.success();
    }
}
