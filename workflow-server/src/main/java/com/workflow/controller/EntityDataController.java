package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.EntityDataDynamicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实体数据控制器
 * 管理实体对应的数据（使用独立表结构）
 */
@RestController
@RequestMapping("/api/entity-data")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityDataController {
    
    private final EntityDataDynamicService entityDataDynamicService;
    
    /**
     * 获取某实体的所有数据
     */
    @GetMapping("/entity/{entityCode}")
    public ApiResponse<List<EntityDataDTO>> listByEntity(@PathVariable String entityCode) {
        return ApiResponse.success(entityDataDynamicService.findByEntityCode(entityCode));
    }
    
    /**
     * 根据ID获取数据详情
     */
    @GetMapping("/entity/{entityCode}/{id}")
    public ApiResponse<EntityDataDTO> getById(@PathVariable String entityCode, @PathVariable String id) {
        return ApiResponse.success(entityDataDynamicService.findById(entityCode, id));
    }
    
    /**
     * 根据流程实例ID获取数据
     */
    @GetMapping("/entity/{entityCode}/process/{processInstanceId}")
    public ApiResponse<EntityDataDTO> getByProcessInstance(
            @PathVariable String entityCode, 
            @PathVariable String processInstanceId) {
        return ApiResponse.success(entityDataDynamicService.findByProcessInstanceId(entityCode, processInstanceId));
    }
    
    /**
     * 保存数据
     * 如果DTO中startProcess为true且实体绑定了流程，则同时发起流程
     */
    @PostMapping
    public ApiResponse<EntityDataDTO> save(@RequestBody EntityDataDTO dto) {
        return ApiResponse.success(entityDataDynamicService.save(dto));
    }
    
    /**
     * 更新数据
     */
    @PutMapping("/entity/{entityCode}/{id}")
    public ApiResponse<EntityDataDTO> update(
            @PathVariable String entityCode, 
            @PathVariable String id, 
            @RequestBody Map<String, Object> formData) {
        return ApiResponse.success(entityDataDynamicService.update(entityCode, id, formData));
    }
    
    /**
     * 删除数据
     */
    @DeleteMapping("/entity/{entityCode}/{id}")
    public ApiResponse<Void> delete(@PathVariable String entityCode, @PathVariable String id) {
        entityDataDynamicService.delete(entityCode, id);
        return ApiResponse.success();
    }
    
    /**
     * 条件查询
     */
    @PostMapping("/entity/{entityCode}/search")
    public ApiResponse<List<EntityDataDTO>> search(
            @PathVariable String entityCode, 
            @RequestBody Map<String, Object> condition) {
        return ApiResponse.success(entityDataDynamicService.findByCondition(entityCode, condition));
    }
    
    /**
     * 统计数量
     */
    @GetMapping("/entity/{entityCode}/count")
    public ApiResponse<Long> count(@PathVariable String entityCode) {
        return ApiResponse.success(entityDataDynamicService.count(entityCode));
    }
}
