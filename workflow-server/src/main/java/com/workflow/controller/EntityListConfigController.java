package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.service.EntityListConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体列表配置控制器
 */
@RestController
@RequestMapping("/api/entity-list-config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityListConfigController {

    private final EntityListConfigService listConfigService;

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

    /**
     * 删除列表配置
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        listConfigService.deleteConfig(id);
        return Result.success();
    }
}
