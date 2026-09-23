package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.PageResult;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.ApiResponse;
import com.workflow.entity.definition.api.request.EntityDefinitionOptionResolveRequest;
import com.workflow.entity.definition.api.response.EntityDefinitionDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionOptionDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionQueryDTO;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.api.request.EntityLifecycleModeRequest;
import com.workflow.entity.definition.api.request.EntityWorkflowBindingRequest;
import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;
import com.workflow.entity.definition.application.EntityDefinitionOptionService;
import com.workflow.entity.definition.application.EntityDefinitionService;
import com.workflow.entity.definition.application.EntityFieldDefinitionService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
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
    private final EntityFieldDefinitionService fieldDefinitionService;
    private final EntityDefinitionOptionService entityOptionService;
    private final EntityActionCapabilityService actionCapabilityService;
    
    /**
     * 获取实体定义分页列表
     *
     * @param query 查询，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 符合条件的实体定义结果，供调用方继续处理
     */
    @GetMapping
    public ApiResponse<PageResult<EntityDefinitionDTO>> list(EntityDefinitionQueryDTO query) {
        return ApiResponse.success(entityService.findPage(query));
    }

    /**
     * 获取实体选择器分页选项。
     *
     * @param query 查询，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 处理后的选项结果，供调用方继续处理
     */
    @GetMapping("/options")
    public ApiResponse<PageResult<EntityDefinitionOptionDTO>> options(EntityDefinitionQueryDTO query) {
        return ApiResponse.success(entityOptionService.findPage(query));
    }

    /**
     * 根据实体 ID 或编码批量回显选择项。
     *
     * @param request 本次请求，后续经校验后用于解析选项
     * @return 解析后的选项结果，供调用方继续处理
     */
    @PostMapping("/options/resolve")
    public ApiResponse<List<EntityDefinitionOptionDTO>> resolveOptions(
            @RequestBody EntityDefinitionOptionResolveRequest request) {
        return ApiResponse.success(entityOptionService.resolve(request));
    }
    
    /**
     * 根据ID获取实体定义
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的API{@code response<entity}定义{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/{id}")
    public ApiResponse<EntityDefinitionDTO> getById(@PathVariable String id) {
        return ApiResponse.success(entityService.findById(id));
    }
    
    /**
     * 根据编码获取实体定义。
     * 运行态列表/表单会按编码读取元数据，不能要求菜单管理权限 entity:definition:view。
     * 登录用户需具备该实体的设计查看权，或任一标准数据动作权限（如 entity:{code}:list）。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的API{@code response<entity}定义{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/code/{entityCode}")
    @AuthenticatedApi(objectAuthorization = true)
    public ApiResponse<EntityDefinitionDTO> getByCode(
            @PathVariable String entityCode) {
        actionCapabilityService.requireEntityMetadataAccess(entityCode);
        return ApiResponse.success(entityService.findByCode(entityCode));
    }
    
    /**
     * 创建实体定义
     *
     * @param dto DTO，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 创建后的实体定义结果，供调用方继续处理
     */
    @PostMapping
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityDefinitionDTO> create(@RequestBody EntityDefinitionDTO dto) {
        return ApiResponse.success(entityService.save(dto));
    }
    
    /**
     * 更新实体定义
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param dto DTO，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 更新后的实体定义结果，供调用方继续处理
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityDefinitionDTO> update(@PathVariable String id, @RequestBody EntityDefinitionDTO dto) {
        return ApiResponse.success(entityService.update(id, dto));
    }

    /**
     * 新增单个实体字段，不提交实体中的其他字段草稿。
     *
     * @param entityId 实体ID，后续用于创建字段时定位或关联目标
     * @param dto DTO，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 创建后的字段结果，供调用方继续处理
     */
    @PostMapping("/{entityId}/fields")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityFieldDTO> createField(
            @PathVariable String entityId,
            @RequestBody EntityFieldDTO dto) {
        return ApiResponse.success(
                fieldDefinitionService.createField(entityId, dto));
    }

    /**
     * 更新单个实体字段，不提交实体中的其他字段草稿。
     *
     * @param entityId 实体ID，后续用于更新字段时定位或关联目标
     * @param fieldId 字段ID，后续用于更新字段时定位或关联目标
     * @param dto DTO，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 更新后的字段结果，供调用方继续处理
     */
    @PostMapping("/{entityId}/fields/{fieldId}/update")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityFieldDTO> updateField(
            @PathVariable String entityId,
            @PathVariable String fieldId,
            @RequestBody EntityFieldDTO dto) {
        return ApiResponse.success(
                fieldDefinitionService.updateField(entityId, fieldId, dto));
    }
    
    /**
     * 删除实体定义
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 删除后的实体定义结果，供调用方继续处理
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<Void> delete(@PathVariable String id) {
        entityService.delete(id);
        return ApiResponse.success();
    }
    
    /**
     * 发布实体定义
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于发布实体定义
     * @return 发布后的实体定义结果，供调用方继续处理
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
     *
     * @param processId 流程ID，后续用于读取流程ID时定位或关联目标
     * @return 符合条件的API{@code response<entity}定义{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/process/{processId}")
    public ApiResponse<EntityDefinitionDTO> getByProcessId(@PathVariable String processId) {
        return ApiResponse.success(entityService.findByProcessDefinitionId(processId));
    }
}
