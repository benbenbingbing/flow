package com.workflow.entity.definition.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.PageResult;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.ApiResponse;
import com.workflow.entity.definition.api.request.EntityDefinitionOptionResolveRequest;
import com.workflow.entity.definition.api.response.EntityDefinitionDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionOptionDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionQueryDTO;
import com.workflow.entity.definition.api.request.EntityLifecycleModeRequest;
import com.workflow.entity.definition.api.request.EntityWorkflowBindingRequest;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.definition.application.EntityDefinitionOptionService;
import com.workflow.entity.definition.application.EntityDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体定义控制器
 * 管理业务实体（对应数据库表）
 */
@RequiresPermission("entity:definition:view")
@RestController
@RequestMapping("/api/entity")
@RequiredArgsConstructor
public class EntityDefinitionController {
    
    private final EntityDefinitionService entityService;
    private final EntityDefinitionOptionService entityOptionService;
    
    /**
     * 获取实体定义分页列表
     */
    @GetMapping
    public ApiResponse<PageResult<EntityDefinitionDTO>> list(EntityDefinitionQueryDTO query) {
        return ApiResponse.success(entityService.findPage(query));
    }

    /**
     * 获取实体选择器分页选项。
     */
    @GetMapping("/options")
    public ApiResponse<PageResult<EntityDefinitionOptionDTO>> options(EntityDefinitionQueryDTO query) {
        return ApiResponse.success(entityOptionService.findPage(query));
    }

    /**
     * 根据实体 ID 或编码批量回显选择项。
     */
    @PostMapping("/options/resolve")
    public ApiResponse<List<EntityDefinitionOptionDTO>> resolveOptions(
            @RequestBody EntityDefinitionOptionResolveRequest request) {
        return ApiResponse.success(entityOptionService.resolve(request));
    }
    
    /**
     * 根据ID获取实体定义
     */
    @GetMapping("/{id}")
    public ApiResponse<EntityDefinitionDTO> getById(@PathVariable String id) {
        return ApiResponse.success(entityService.findById(id));
    }
    
    /**
     * 根据编码获取实体定义
     */
    @GetMapping("/code/{code}")
    public ApiResponse<EntityDefinitionDTO> getByCode(@PathVariable String code) {
        return ApiResponse.success(entityService.findByCode(code));
    }
    
    /**
     * 创建实体定义
     */
    @PostMapping
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityDefinitionDTO> create(@RequestBody EntityDefinitionDTO dto) {
        return ApiResponse.success(entityService.save(dto));
    }
    
    /**
     * 更新实体定义
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityDefinitionDTO> update(@PathVariable String id, @RequestBody EntityDefinitionDTO dto) {
        return ApiResponse.success(entityService.update(id, dto));
    }
    
    /**
     * 删除实体定义
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<Void> delete(@PathVariable String id) {
        entityService.delete(id);
        return ApiResponse.success();
    }
    
    /**
     * 发布实体定义
     */
    @PostMapping("/{id}/publish")
    @RequiresPermission("entity:definition:publish")
    public ApiResponse<EntityDefinitionDTO> publish(
            @PathVariable String id,
            @RequestBody(required = false) ConfigMigrationPublishRequest request) {
        String userId = UserContext.getUserId();
        String userName = UserContext.getUsername();
        return ApiResponse.success(entityService.publish(id, userId, userName, request));
    }
    
    /**
     * 绑定工作流到实体。POST /api/entity/{entityId}/workflow-binding/update
     *
     * @param entityId 实体ID
     * @param request  工作流绑定请求（含流程定义ID）
     * @return 更新后的实体定义
     */
    @PostMapping("/{entityId}/workflow-binding/update")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityDefinitionDTO> bindWorkflow(
            @PathVariable String entityId,
            @RequestBody EntityWorkflowBindingRequest request) {
        return ApiResponse.success(entityService.bindWorkflow(
                entityId,
                request.getProcessDefinitionId()));
    }

    /**
     * 解除实体绑定的工作流。POST /api/entity/{entityId}/workflow-binding/delete
     *
     * @param entityId 实体ID
     * @return 更新后的实体定义
     */
    @PostMapping("/{entityId}/workflow-binding/delete")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityDefinitionDTO> unbindWorkflow(@PathVariable String entityId) {
        return ApiResponse.success(entityService.unbindWorkflow(entityId));
    }

    /**
     * 更新实体生命周期模式。POST /api/entity/{entityId}/lifecycle-mode
     *
     * @param entityId 实体ID
     * @param request  生命周期模式请求
     * @return 更新后的实体定义
     */
    @PostMapping("/{entityId}/lifecycle-mode")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityDefinitionDTO> updateLifecycleMode(
            @PathVariable String entityId,
            @RequestBody EntityLifecycleModeRequest request) {
        return ApiResponse.success(entityService.updateLifecycleMode(
                entityId,
                request.getLifecycleMode()));
    }

    /**
     * 根据流程定义ID查询绑定的实体
     */
    @GetMapping("/process/{processId}")
    public ApiResponse<EntityDefinitionDTO> getByProcessId(@PathVariable String processId) {
        return ApiResponse.success(entityService.findByProcessDefinitionId(processId));
    }
}
