package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.dto.ListFieldDataSourceOptionDTO;
import com.workflow.dto.permission.EntityActionCapabilityDTO;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityListConfigService;
import com.workflow.service.permission.EntityActionCapabilityService;
import com.workflow.service.listfield.ListFieldDataProviderRegistry;
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
    private final EntityDataDynamicService entityDataDynamicService;
    private final EntityActionCapabilityService actionCapabilityService;
    private final ListFieldDataProviderRegistry dataProviderRegistry;

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
    public Result<EntityListConfigDTO> save(@RequestBody EntityListConfigDTO dto) {
        EntityListConfigDTO saved = listConfigService.saveConfig(dto);
        return Result.success(saved);
    }

    @PostMapping("/{id}/action-rule/preview")
    public Result<EntityActionCapabilityDTO> previewActionRule(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        EntityListConfigDTO config = listConfigService.findById(id);
        if (config == null) {
            throw new RuntimeException("列表配置不存在");
        }
        String buttonKey = request.get("buttonKey");
        String entityDataId = request.get("entityDataId");
        var row = entityDataDynamicService.findAccessibleById(
                config.getEntityCode(),
                entityDataId,
                config.getId());
        return Result.success(actionCapabilityService.evaluateRowAction(
                config.getEntityCode(),
                config.getListKey(),
                buttonKey,
                row));
    }

    /**
     * 删除列表配置
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        listConfigService.deleteConfig(id);
        return Result.success();
    }
}
