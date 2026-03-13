package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.service.EntityDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体定义控制器
 * 管理业务实体（对应数据库表）
 */
@RestController
@RequestMapping("/api/entity")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityDefinitionController {
    
    private final EntityDefinitionService entityService;
    
    /**
     * 获取所有实体定义
     */
    @GetMapping
    public ApiResponse<List<EntityDefinitionDTO>> list() {
        return ApiResponse.success(entityService.findAll());
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
    public ApiResponse<EntityDefinitionDTO> create(@RequestBody EntityDefinitionDTO dto) {
        return ApiResponse.success(entityService.save(dto));
    }
    
    /**
     * 更新实体定义
     */
    @PutMapping("/{id}")
    public ApiResponse<EntityDefinitionDTO> update(@PathVariable String id, @RequestBody EntityDefinitionDTO dto) {
        return ApiResponse.success(entityService.update(id, dto));
    }
    
    /**
     * 删除实体定义
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        entityService.delete(id);
        return ApiResponse.success();
    }
    
    /**
     * 发布实体定义
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<EntityDefinitionDTO> publish(@PathVariable String id) {
        return ApiResponse.success(entityService.publish(id));
    }
    
    /**
     * 绑定流程
     */
    @PostMapping("/{entityId}/bind-process/{processId}")
    public ApiResponse<EntityDefinitionDTO> bindProcess(
            @PathVariable String entityId,
            @PathVariable String processId) {
        return ApiResponse.success(entityService.bindProcess(entityId, processId));
    }

    /**
     * 根据流程定义ID查询绑定的实体
     */
    @GetMapping("/process/{processId}")
    public ApiResponse<EntityDefinitionDTO> getByProcessId(@PathVariable String processId) {
        return ApiResponse.success(entityService.findByProcessDefinitionId(processId));
    }
}
