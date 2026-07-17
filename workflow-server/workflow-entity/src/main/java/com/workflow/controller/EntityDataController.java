package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.EntityBatchDeleteRequest;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.EntityDataActionService;
import com.workflow.dto.EntityDataExportRequest;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityDataExportService;
import com.workflow.service.EntityDataListConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
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
    private final EntityDataExportService entityDataExportService;
    private final EntityDataActionService entityDataActionService;
    private final com.workflow.service.permission.EntityActionCapabilityService actionCapabilityService;
    
    /**
     * 获取某实体的所有数据（支持查询条件）
     */
    @GetMapping("/entity/{entityCode}")
    public ApiResponse<?> listByEntity(
            @PathVariable String entityCode,
            @RequestParam(required = false) Map<String, String> params) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.service.permission.EntityPermissionAction.LIST);
        boolean paged = hasPaging(params);
        long pageNum = positiveLong(params, "pageNum", "page", 1);
        long pageSize = positiveLong(params, "pageSize", "size", 10);
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
                if (paged) {
                    return ApiResponse.success(
                            entityDataListConfigService.findPageWithConfig(
                                    entityCode,
                                    null,
                                    condition,
                                    pageNum,
                                    pageSize));
                }
                return ApiResponse.success(entityDataListConfigService.findListWithConfig(entityCode, null, condition));
            }
        }
        if (paged) {
            return ApiResponse.success(
                    entityDataListConfigService.findPageWithConfig(
                            entityCode,
                            null,
                            null,
                            pageNum,
                            pageSize));
        }
        return ApiResponse.success(entityDataListConfigService.findListWithConfig(entityCode, null, null));
    }

    /**
     * 获取某实体的数据列表（带列表配置扩展字段）
     */
    @GetMapping("/entity/{entityCode}/list-with-config")
    public ApiResponse<?> listWithConfig(
            @PathVariable String entityCode,
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) Map<String, String> params) {
        Map<String, Object> condition = new java.util.HashMap<>();
        boolean paged = hasPaging(params);
        long pageNum = positiveLong(params, "pageNum", "page", 1);
        long pageSize = positiveLong(params, "pageSize", "size", 10);
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.service.permission.EntityPermissionAction.LIST);
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
        if (paged) {
            return ApiResponse.success(entityDataListConfigService.findPageWithConfig(
                    entityCode,
                    listKey,
                    condition.isEmpty() ? null : condition,
                    pageNum,
                    pageSize));
        }
        return ApiResponse.success(entityDataListConfigService.findListWithConfig(
                entityCode, listKey, condition.isEmpty() ? null : condition));
    }
    
    /**
     * 根据ID获取数据详情
     */
    @GetMapping("/entity/{entityCode}/detail/{id}")
    public ApiResponse<EntityDataDTO> getById(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestParam(required = false) String listKey) {
        return ApiResponse.success(entityDataActionService.getDetail(entityCode, id, listKey));
    }
    
    /**
     * 根据流程实例ID获取数据
     */
    @GetMapping("/entity/{entityCode}/process/{processInstanceId}")
    public ApiResponse<EntityDataDTO> getByProcessInstance(
            @PathVariable String entityCode, 
            @PathVariable String processInstanceId,
            @RequestParam(required = false) String listKey) {
        actionCapabilityService.requireAnyStandardPermission(
                entityCode,
                com.workflow.service.permission.EntityPermissionAction.VIEW,
                com.workflow.service.permission.EntityPermissionAction.APPROVE);
        return ApiResponse.success(entityDataActionService.getDetailByProcessInstance(
                entityCode,
                processInstanceId,
                listKey));
    }
    
    /**
     * 保存数据
     * 如果DTO中startProcess为true且实体绑定了流程，则同时发起流程
     */
    @PostMapping
    public ApiResponse<EntityDataDTO> save(@RequestBody EntityDataDTO dto) {
        return ApiResponse.success(entityDataActionService.create(dto));
    }
    
    /**
     * 更新数据
     */
    @PutMapping("/entity/{entityCode}/detail/{id}")
    public ApiResponse<EntityDataDTO> update(
            @PathVariable String entityCode, 
            @PathVariable String id, 
            @RequestParam(required = false) String listKey,
            @RequestBody Map<String, Object> formData) {
        return ApiResponse.success(entityDataActionService.update(entityCode, id, listKey, formData));
    }
    
    /**
     * 删除数据
     */
    @DeleteMapping("/entity/{entityCode}/detail/{id}")
    public ApiResponse<Void> delete(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestParam(required = false) String listKey) {
        entityDataActionService.delete(entityCode, id, listKey);
        return ApiResponse.success();
    }

    @PostMapping("/entity/{entityCode}/batch-delete")
    public ApiResponse<Void> batchDelete(
            @PathVariable String entityCode,
            @RequestBody EntityBatchDeleteRequest request) {
        entityDataActionService.batchDelete(entityCode, request.getIds(), request.getListKey());
        return ApiResponse.success();
    }
    
    /**
     * 条件查询
     */
    @PostMapping("/entity/{entityCode}/search")
    public ApiResponse<List<EntityDataDTO>> search(
            @PathVariable String entityCode, 
            @RequestBody Map<String, Object> condition) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.service.permission.EntityPermissionAction.LIST);
        return ApiResponse.success(entityDataListConfigService.findListWithConfig(entityCode, null, condition));
    }
    
    /**
     * 统计数量
     */
    @GetMapping("/entity/{entityCode}/count")
    public ApiResponse<Long> count(@PathVariable String entityCode) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.service.permission.EntityPermissionAction.LIST);
        return ApiResponse.success(entityDataDynamicService.count(entityCode));
    }

    /**
     * 导出实体数据（选中或全部）
     */
    @PostMapping("/entity/{entityCode}/export")
    public void export(
            @PathVariable String entityCode,
            @RequestBody EntityDataExportRequest request,
            HttpServletResponse response) {
        entityDataExportService.export(entityCode, request, response);
    }

    private boolean hasPaging(Map<String, String> params) {
        return params != null && (
                params.containsKey("pageNum")
                        || params.containsKey("page")
                        || params.containsKey("pageSize")
                        || params.containsKey("size"));
    }

    private long positiveLong(
            Map<String, String> params,
            String primaryKey,
            String fallbackKey,
            long defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        String value = params.get(primaryKey);
        if (value == null || value.isBlank()) {
            value = params.get(fallbackKey);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(primaryKey + " 必须是正整数");
        }
    }
}
