package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.EntityDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体数据控制器
 * 管理实体对应的数据
 */
@RestController
@RequestMapping("/api/entity-data")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityDataController {
    
    private final EntityDataService entityDataService;
    
    /**
     * 获取某实体的所有数据
     */
    @GetMapping("/entity/{entityCode}")
    public ApiResponse<List<EntityDataDTO>> listByEntity(@PathVariable String entityCode) {
        return ApiResponse.success(entityDataService.findByEntityCode(entityCode));
    }
    
    /**
     * 根据ID获取数据详情
     */
    @GetMapping("/{id}")
    public ApiResponse<EntityDataDTO> getById(@PathVariable String id) {
        return ApiResponse.success(entityDataService.findById(id));
    }
    
    /**
     * 根据流程实例ID获取数据
     */
    @GetMapping("/process/{processInstanceId}")
    public ApiResponse<EntityDataDTO> getByProcessInstance(@PathVariable String processInstanceId) {
        return ApiResponse.success(entityDataService.findByProcessInstanceId(processInstanceId));
    }
    
    /**
     * 保存数据
     * 如果DTO中startProcess为true且实体绑定了流程，则同时发起流程
     */
    @PostMapping
    public ApiResponse<EntityDataDTO> save(@RequestBody EntityDataDTO dto) {
        return ApiResponse.success(entityDataService.save(dto));
    }
    
    /**
     * 更新数据
     */
    @PutMapping("/{id}")
    public ApiResponse<EntityDataDTO> update(@PathVariable String id, @RequestBody EntityDataDTO dto) {
        return ApiResponse.success(entityDataService.update(id, dto));
    }
    
    /**
     * 删除数据
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        entityDataService.delete(id);
        return ApiResponse.success();
    }
}
