package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityDataListConfigService;
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
    private final EntityDataListConfigService entityDataListConfigService;
    
    /**
     * 获取某实体的所有数据（支持查询条件）
     */
    @GetMapping("/entity/{entityCode}")
    public ApiResponse<List<EntityDataDTO>> listByEntity(
            @PathVariable String entityCode,
            @RequestParam(required = false) Map<String, String> params) {
        if (params != null && !params.isEmpty()) {
            Map<String, Object> condition = new java.util.HashMap<>();
            params.forEach((k, v) -> {
                if (v != null && !v.trim().isEmpty()) {
                    condition.put(k, v);
                }
            });
            // 排除分页和排序等系统参数，避免被当作查询条件
            condition.remove("pageNum");
            condition.remove("pageSize");
            condition.remove("page");
            condition.remove("size");
            condition.remove("offset");
            condition.remove("limit");
            condition.remove("sort");
            condition.remove("orderBy");
            condition.remove("order");
            if (!condition.isEmpty()) {
                return ApiResponse.success(entityDataDynamicService.findByCondition(entityCode, condition));
            }
        }
        return ApiResponse.success(entityDataDynamicService.findByEntityCode(entityCode));
    }

    /**
     * 获取某实体的数据列表（带列表配置扩展字段）
     */
    @GetMapping("/entity/{entityCode}/list-with-config")
    public ApiResponse<List<EntityDataDTO>> listWithConfig(
            @PathVariable String entityCode,
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) Map<String, String> params) {
        Map<String, Object> condition = new java.util.HashMap<>();
        if (params != null && !params.isEmpty()) {
            params.forEach((k, v) -> {
                if (v != null && !v.trim().isEmpty()) {
                    condition.put(k, v);
                }
            });
            // 排除系统参数
            condition.remove("listKey");
            condition.remove("pageNum");
            condition.remove("pageSize");
            condition.remove("page");
            condition.remove("size");
            condition.remove("offset");
            condition.remove("limit");
            condition.remove("sort");
            condition.remove("orderBy");
            condition.remove("order");
        }
        return ApiResponse.success(entityDataListConfigService.findListWithConfig(
                entityCode, listKey, condition.isEmpty() ? null : condition));
    }
    
    /**
     * 根据ID获取数据详情
     */
    @GetMapping("/entity/{entityCode}/detail/{id}")
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
    @PutMapping("/entity/{entityCode}/detail/{id}")
    public ApiResponse<EntityDataDTO> update(
            @PathVariable String entityCode, 
            @PathVariable String id, 
            @RequestBody Map<String, Object> formData) {
        return ApiResponse.success(entityDataDynamicService.update(entityCode, id, formData));
    }
    
    /**
     * 删除数据
     */
    @DeleteMapping("/entity/{entityCode}/detail/{id}")
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
