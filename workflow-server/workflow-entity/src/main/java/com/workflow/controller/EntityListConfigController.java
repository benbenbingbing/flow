package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.dto.EntityListFieldDeleteRequest;
import com.workflow.dto.EntityListFieldSaveRequest;
import com.workflow.dto.EntityListItemReorderRequest;
import com.workflow.dto.EntityListMetadataPatchRequest;
import com.workflow.dto.ListFieldDataSourceOptionDTO;
import com.workflow.dto.permission.EntityActionCapabilityDTO;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityListConfigService;
import com.workflow.service.UiConfigDraftMetadataService;
import com.workflow.service.UiConfigurationAccessService;
import com.workflow.service.permission.EntityActionCapabilityService;
import com.workflow.service.listfield.ListFieldDataProviderRegistry;
import com.workflow.entity.EntityListField;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实体列表配置控制器
 */
@RestController
@RequestMapping("/api/entity-list-config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityListConfigController {

    private final EntityListConfigService listConfigService;
    private final UiConfigDraftMetadataService metadataService;
    private final EntityDataDynamicService entityDataDynamicService;
    private final EntityActionCapabilityService actionCapabilityService;
    private final ListFieldDataProviderRegistry dataProviderRegistry;
    private final UiConfigurationAccessService accessService;

    /**
     * 查询列表字段数据源扩展选项（可用数据源提供方）。GET /api/entity-list-config/extension-options
     *
     * @return 扩展选项列表
     */
    @GetMapping("/extension-options")
    public Result<List<ListFieldDataSourceOptionDTO>> extensionOptions() {
        return Result.success(dataProviderRegistry.getOptions());
    }

    /**
     * 查询实体的所有列表配置。GET /api/entity-list-config/entity/{entityId}
     *
     * @param entityId 实体ID
     * @return 列表配置列表
     */
    @GetMapping("/entity/{entityId}")
    public Result<List<EntityListConfigDTO>> listByEntityId(@PathVariable String entityId) {
        List<EntityListConfigDTO> list = listConfigService.findByEntityId(entityId);
        return Result.success(list);
    }

    /**
     * 根据ID查询配置（含字段）
     */
    @GetMapping("/{id}")
    public Result<EntityListConfigDTO> getById(@PathVariable String id) {
        EntityListConfigDTO dto = listConfigService.findById(id);
        return Result.success(dto);
    }

    /**
     * 保存/更新列表配置
     */
    @PostMapping("/save")
    public Result<EntityListConfigDTO> saveRequest(
            @RequestBody EntityListConfigDTO dto) {
        accessService.requireNewListAccess(dto);
        EntityListConfigDTO saved = listConfigService.saveConfig(
                dto,
                dto.getExpectedRevision());
        return Result.success(saved);
    }

    /**
     * 保留给既有直接调用测试的兼容入口；HTTP API 使用 saveRequest。
     */
    @Deprecated
    public Result<EntityListConfigDTO> save(EntityListConfigDTO dto) {
        accessService.requireNewListAccess(dto);
        return Result.success(listConfigService.saveConfig(dto));
    }

    /**
     * 增量更新列表配置元数据。POST /api/entity-list-config/{id}
     *
     * @param id      列表配置ID
     * @param request 元数据补丁请求
     * @return 更新后的列表配置
     */
    @PostMapping("/{id}")
    public Result<EntityListConfigDTO> patchMetadata(
            @PathVariable String id,
            @RequestBody EntityListMetadataPatchRequest request) {
        accessService.requireListAccess(id);
        return Result.success(metadataService.patchList(id, request));
    }

    /**
     * 新增列表字段。POST /api/entity-list-config/{id}/fields
     *
     * @param id      列表配置ID
     * @param request 字段保存请求
     * @return 创建后的列表字段
     */
    @PostMapping("/{id}/fields")
    public Result<EntityListField> createField(
            @PathVariable String id,
            @RequestBody EntityListFieldSaveRequest request) {
        accessService.requireListAccess(id);
        return Result.success(listConfigService.createField(id, request));
    }

    /**
     * 增量更新列表字段。POST /api/entity-list-config/{id}/fields/{fieldId}/patch
     *
     * @param id       列表配置ID
     * @param fieldId  字段ID
     * @param request  字段保存请求
     * @return 更新后的列表字段
     */
    @PostMapping("/{id}/fields/{fieldId}/patch")
    public Result<EntityListField> patchField(
            @PathVariable String id,
            @PathVariable String fieldId,
            @RequestBody EntityListFieldSaveRequest request) {
        accessService.requireListAccess(id);
        return Result.success(
                listConfigService.patchField(id, fieldId, request));
    }

    /**
     * 调整列表字段排序。POST /api/entity-list-config/{id}/fields/{fieldId}/order
     *
     * @param id       列表配置ID
     * @param fieldId  字段ID
     * @param request  排序请求
     * @return 排序后的列表字段
     */
    @PostMapping("/{id}/fields/{fieldId}/order")
    public Result<EntityListField> reorderField(
            @PathVariable String id,
            @PathVariable String fieldId,
            @RequestBody EntityListItemReorderRequest request) {
        accessService.requireListAccess(id);
        return Result.success(
                listConfigService.reorderField(id, fieldId, request));
    }

    /**
     * 删除列表字段（乐观锁校验）。POST /api/entity-list-config/{id}/fields/{fieldId}/delete
     *
     * @param id       列表配置ID
     * @param fieldId  字段ID
     * @param request  删除请求，携带期望版本号
     * @return 无数据返回
     */
    @PostMapping("/{id}/fields/{fieldId}/delete")
    public Result<Void> deleteField(
            @PathVariable String id,
            @PathVariable String fieldId,
            @RequestBody EntityListFieldDeleteRequest request) {
        accessService.requireListAccess(id);
        listConfigService.deleteField(id, fieldId, request.getExpectedRevision());
        return Result.success();
    }

    /**
     * 预览行级动作规则在指定数据行上的求值结果。POST /api/entity-list-config/{id}/action-rule/preview
     *
     * @param id      列表配置ID
     * @param request 含 buttonKey 与 entityDataId 的预览请求
     * @return 动作能力求值结果
     */
    @PostMapping("/{id}/action-rule/preview")
    public Result<EntityActionCapabilityDTO> previewActionRule(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        accessService.requireListAccess(id);
        EntityListConfigDTO config = listConfigService.findById(id);
        if (config == null) {
            throw new RuntimeException("列表配置不存在");
        }
        String buttonKey = request.get("buttonKey");
        String entityDataId = request.get("entityDataId");
        var row = entityDataDynamicService.findAccessibleById(
                config.getEntityCode(),
                entityDataId,
                config.getListKey());
        return Result.success(actionCapabilityService.evaluateRowAction(
                config.getEntityCode(),
                config.getListKey(),
                buttonKey,
                row));
    }

    /**
     * 删除列表配置
     */
    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        accessService.requireListAccess(id);
        listConfigService.deleteConfig(id);
        return Result.success();
    }
}
