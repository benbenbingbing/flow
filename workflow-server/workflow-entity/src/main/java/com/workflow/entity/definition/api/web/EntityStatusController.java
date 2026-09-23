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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体状态结果，供调用方继续处理
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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param category 类别，决定后续状态或结果的归类
     * @return 符合条件的实体状态结果，供调用方继续处理
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
     *
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @return 保存后的实体状态结果，供调用方继续处理
     */
    @PostMapping("/save")
    @RequiresPermission("entity:definition:manage")
    public Result<Void> save(@RequestBody EntityStatus status) {
        entityStatusService.saveStatus(status);
        return Result.success();
    }
    
    /**
     * 批量保存实体状态
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param statuses {@code statuses}，作为 {@code entityStatusService.saveStatusList} 的输入影响后续处理
     * @return 保存后的实体状态列表结果，供调用方继续处理
     */
    @PostMapping("/save-list/{entityCode}")
    @RequiresPermission("entity:definition:manage")
    public Result<Void> saveList(@PathVariable String entityCode, @RequestBody List<EntityStatus> statuses) {
        entityStatusService.saveStatusList(entityCode, statuses);
        return Result.success();
    }
    
    /**
     * 删除实体状态
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 删除后的实体状态结果，供调用方继续处理
     */
    @PostMapping("/delete/{id}")
    @RequiresPermission("entity:definition:manage")
    public Result<Void> delete(@PathVariable String id) {
        entityStatusService.deleteStatus(id);
        return Result.success();
    }
}
