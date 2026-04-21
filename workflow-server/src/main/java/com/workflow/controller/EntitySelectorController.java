package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityField;
import com.workflow.service.DynamicTableService;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityFieldService;
import com.workflow.service.SystemEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体选择器控制器
 * 提供通用接口用于选择实体数据（支持用户实体和系统实体）
 */
@RestController
@RequestMapping("/api/entity-selector")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntitySelectorController {

    private final EntityDataDynamicService entityDataDynamicService;
    private final DynamicTableService dynamicTableService;
    private final SystemEntityService systemEntityService;
    private final EntityFieldService entityFieldService;

    /**
     * 查询实体数据列表（用于选择器）
     * 支持用户实体（CUSTOM）和系统实体（USER/DEPT/ROLE/GROUP）
     *
     * @param entityType 实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）
     * @param entityCode 实体编码（CUSTOM类型时必填）
     * @param keyword 搜索关键词（匹配name、code）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 实体数据列表
     */
    @GetMapping("/{entityType}")
    public Result<Map<String, Object>> selectList(
            @PathVariable String entityType,
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
        // 根据实体类型选择查询方式
        if ("CUSTOM".equalsIgnoreCase(entityType)) {
            // 用户自定义实体
            if (entityCode == null || entityCode.isEmpty()) {
                return Result.error("查询用户实体时 entityCode 不能为空");
            }
            return selectCustomEntity(entityCode, keyword, pageNum, pageSize);
        } else {
            // 系统实体（USER/DEPT/ROLE/GROUP）
            return Result.success(systemEntityService.selectList(entityType, keyword, pageNum, pageSize));
        }
    }

    /**
     * 根据ID查询实体数据详情（用于选择器回显）
     *
     * @param entityType 实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）
     * @param id 数据ID
     * @param entityCode 实体编码（CUSTOM类型时必填）
     * @return 实体数据
     */
    @GetMapping("/{entityType}/{id}")
    public Result<Map<String, Object>> getById(
            @PathVariable String entityType,
            @PathVariable String id,
            @RequestParam(required = false) String entityCode) {
        
        Map<String, Object> data;
        
        if ("CUSTOM".equalsIgnoreCase(entityType)) {
            if (entityCode == null || entityCode.isEmpty()) {
                return Result.error("查询用户实体时 entityCode 不能为空");
            }
            if (!dynamicTableService.tableExists(entityCode)) {
                return Result.error("实体数据表不存在: " + entityCode);
            }
            data = entityDataDynamicService.findById(entityCode, id).getData();
            if (data != null) {
                data = simplifyEntityData(data);
                data.put("entityType", "CUSTOM");
            }
        } else {
            data = systemEntityService.selectById(entityType, id);
        }
        
        return Result.success(data);
    }

    /**
     * 批量查询实体数据（用于选择器回显多个值）
     *
     * @param entityType 实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）
     * @param ids ID列表，逗号分隔
     * @param entityCode 实体编码（CUSTOM类型时必填）
     * @return 实体数据列表
     */
    @GetMapping("/{entityType}/batch")
    public Result<List<Map<String, Object>>> getBatch(
            @PathVariable String entityType,
            @RequestParam String ids,
            @RequestParam(required = false) String entityCode) {
        
        if (ids == null || ids.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        
        List<String> idList = Arrays.asList(ids.split(","));
        List<Map<String, Object>> result = new ArrayList<>();
        
        if ("CUSTOM".equalsIgnoreCase(entityType)) {
            if (entityCode == null || entityCode.isEmpty()) {
                return Result.error("查询用户实体时 entityCode 不能为空");
            }
            if (!dynamicTableService.tableExists(entityCode)) {
                return Result.error("实体数据表不存在: " + entityCode);
            }
            
            for (String id : idList) {
                try {
                    Map<String, Object> data = entityDataDynamicService.findById(entityCode, id.trim()).getData();
                    if (data != null) {
                        result.add(simplifyEntityData(data));
                    }
                } catch (Exception e) {
                    // 忽略不存在的记录
                }
            }
        } else {
            result = systemEntityService.selectBatch(entityType, idList);
        }
        
        return Result.success(result);
    }

    /**
     * 查询实体的引用配置信息
     * 用于前端获取实体类型和字段配置
     */
    @GetMapping("/config/{fieldId}")
    public Result<Map<String, Object>> getEntityConfig(@PathVariable String fieldId) {
        EntityField field = entityFieldService.getById(fieldId);
        if (field == null) {
            return Result.error("字段不存在: " + fieldId);
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put("fieldCode", field.getFieldCode());
        config.put("fieldName", field.getFieldName());
        config.put("fieldType", field.getFieldType());
        config.put("refEntityType", field.getRefEntityType());
        config.put("refEntityId", field.getRefEntityId());
        
        // 根据引用类型返回对应的信息
        if (field.getRefEntityType() == EntityField.RefEntityType.CUSTOM) {
            // 用户实体，返回实体编码
            // 需要通过 refEntityId 查询实体编码
            config.put("entityType", "CUSTOM");
            config.put("entityCode", field.getRefEntityId()); // 这里假设 refEntityId 存储的是 entityCode
        } else {
            // 系统实体
            config.put("entityType", field.getRefEntityType().name());
        }
        
        return Result.success(config);
    }

    // ========== 私有方法 ==========

    /**
     * 查询用户自定义实体
     */
    private Result<Map<String, Object>> selectCustomEntity(String entityCode, String keyword, 
                                                           Integer pageNum, Integer pageSize) {
        if (!dynamicTableService.tableExists(entityCode)) {
            return Result.error("实体数据表不存在: " + entityCode);
        }
        
        // 查询数据
        List<Map<String, Object>> list = entityDataDynamicService.findByEntityCodeSimple(entityCode);
        
        // 如果有搜索关键词，进行过滤
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            list = list.stream()
                    .filter(data -> {
                        String name = getStringValue(data, "name");
                        String code = getStringValue(data, "code");
                        return (name != null && name.toLowerCase().contains(lowerKeyword)) ||
                               (code != null && code.toLowerCase().contains(lowerKeyword));
                    })
                    .collect(Collectors.toList());
        }
        
        // 手动分页
        int total = list.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        List<Map<String, Object>> records = start < total ? list.subList(start, end) : new ArrayList<>();
        
        // 只保留关键字段
        List<Map<String, Object>> simplified = records.stream()
                .map(this::simplifyEntityData)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("records", simplified);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        
        return Result.success(result);
    }

    /**
     * 简化实体数据，只保留关键字段
     */
    private Map<String, Object> simplifyEntityData(Map<String, Object> data) {
        Map<String, Object> simplified = new HashMap<>();
        simplified.put("id", data.get("id"));
        simplified.put("name", data.get("name"));
        simplified.put("code", data.get("code"));
        simplified.put("dataNo", data.get("data_no"));
        simplified.put("title", data.get("title"));
        simplified.put("status", data.get("status"));
        simplified.put("entityType", "CUSTOM");
        return simplified;
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
}
