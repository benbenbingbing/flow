package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityListConfigDTO;
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

    @GetMapping("/extension-options")
    public Result<List<ListFieldDataSourceOptionDTO>> extensionOptions() {
        return Result.success(dataProviderRegistry.getOptions());
    }

    /**
     * 查询实体的所有列表配置
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

    @PostMapping("/{id}")
    public Result<EntityListConfigDTO> patchMetadata(
            @PathVariable String id,
            @RequestBody EntityListMetadataPatchRequest request) {
        accessService.requireListAccess(id);
        return Result.success(metadataService.patchList(id, request));
    }

    @PostMapping("/{id}/fields")
    public Result<EntityListField> createField(
            @PathVariable String id,
            @RequestBody EntityListFieldSaveRequest request) {
        accessService.requireListAccess(id);
        return Result.success(listConfigService.createField(id, request));
    }

    @PostMapping("/{id}/fields/{fieldId}/patch")
    public Result<EntityListField> patchField(
            @PathVariable String id,
            @PathVariable String fieldId,
            @RequestBody EntityListFieldSaveRequest request) {
        accessService.requireListAccess(id);
        return Result.success(
                listConfigService.patchField(id, fieldId, request));
    }

    @PostMapping("/{id}/fields/{fieldId}/order")
    public Result<EntityListField> reorderField(
            @PathVariable String id,
            @PathVariable String fieldId,
            @RequestBody EntityListItemReorderRequest request) {
        accessService.requireListAccess(id);
        return Result.success(
                listConfigService.reorderField(id, fieldId, request));
    }

    @PostMapping("/{id}/fields/{fieldId}/delete")
    public Result<Void> deleteField(
            @PathVariable String id,
            @PathVariable String fieldId,
            @RequestParam Integer expectedRevision) {
        accessService.requireListAccess(id);
        listConfigService.deleteField(id, fieldId, expectedRevision);
        return Result.success();
    }

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
