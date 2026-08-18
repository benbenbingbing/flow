package com.workflow.entity.data.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.Result;
import com.workflow.core.result.PageRequest;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.EntityDefinitionService;
import com.workflow.entity.definition.application.EntityFieldService;
import com.workflow.entity.definition.application.SystemEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体选择器控制器
 * 提供通用接口用于选择实体数据（支持用户实体和系统实体）
 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/entity-selector")
@RequiredArgsConstructor
public class EntitySelectorController {

    private final EntityDataDynamicService entityDataDynamicService;
    private final DynamicTableService dynamicTableService;
    private final SystemEntityService systemEntityService;
    private final EntityFieldService entityFieldService;
    private final EntityDefinitionService entityDefinitionService;
    private final SystemEntityReadService systemEntityReadService;

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
            @RequestParam(required = false) String refEntityId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
        // 根据实体类型选择查询方式
        if ("CUSTOM".equalsIgnoreCase(entityType)) {
            TargetEntity target = resolveTargetEntity(
                    entityCode, refEntityId);
            if (target == null) {
                return Result.error("查询用户实体时 entityCode 不能为空");
            }
            if (target.system()) {
                return selectSystemEntity(
                        target.entityCode(),
                        keyword,
                        pageNum,
                        pageSize);
            }
            return selectCustomEntity(
                    target.entityCode(),
                    keyword,
                    pageNum,
                    pageSize);
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
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String refEntityId) {
        
        Map<String, Object> data;
        TargetEntity target = resolveTargetEntity(
                entityCode, refEntityId);
        
        if ("CUSTOM".equalsIgnoreCase(entityType)) {
            if (target == null) {
                return Result.error("查询用户实体时 entityCode 不能为空");
            }
            if (target.system()) {
                EntityDataDTO detail = systemEntityReadService
                        .findById(target.entityCode(), id);
                data = simplifyEntityData(detail);
                data.put("entityType", "CUSTOM");
                return Result.success(data);
            }
            if (!dynamicTableService.tableExists(target.entityCode())) {
                return Result.error(
                        "实体数据表不存在: " + target.entityCode());
            }
            EntityDataDTO detail = entityDataDynamicService
                    .findAccessibleById(
                            target.entityCode(), id, null);
            if (detail != null) {
                data = simplifyEntityData(detail);
                data.put("entityType", "CUSTOM");
            } else {
                data = null;
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
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String refEntityId,
            @RequestParam(defaultValue = "id") String valueKey) {
        
        if (ids == null || ids.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        
        List<String> idList = Arrays.asList(ids.split(","));
        List<Map<String, Object>> result = new ArrayList<>();
        TargetEntity target = resolveTargetEntity(
                entityCode, refEntityId);
        
        if ("CUSTOM".equalsIgnoreCase(entityType)) {
            if (!"id".equalsIgnoreCase(valueKey)) {
                return Result.error("自定义实体选择器仅支持通过 ID 回显");
            }
            if (target == null) {
                return Result.error("查询用户实体时 entityCode 不能为空");
            }
            if (target.system()) {
                systemEntityReadService.requirePermissions(
                        target.entityCode());
                for (String id : idList) {
                    try {
                        EntityDataDTO detail =
                                systemEntityReadService.findById(
                                        target.entityCode(),
                                        id.trim());
                        result.add(simplifyEntityData(detail));
                    } catch (ForbiddenException e) {
                        // 权限已预检，此处仅忽略不存在的单条记录。
                    }
                }
                return Result.success(result);
            }
            if (!dynamicTableService.tableExists(target.entityCode())) {
                return Result.error(
                        "实体数据表不存在: " + target.entityCode());
            }
            
            for (String id : idList) {
                try {
                    EntityDataDTO detail = entityDataDynamicService
                            .findAccessibleById(
                                    target.entityCode(),
                                    id.trim(),
                                    null);
                    if (detail != null) {
                        result.add(simplifyEntityData(detail));
                    }
                } catch (Exception e) {
                    // 忽略不存在的记录
                }
            }
        } else {
            // USER/DEPT/ROLE/GROUP 运行态回显走选择器公开字段，
            // 不能要求组织管理等后台权限。
            result = systemEntityService.selectBatch(
                    entityType,
                    idList,
                    valueKey);
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
            // 用户实体，通过 refEntityId（实体定义ID）查询实体编码
            config.put("entityType", "CUSTOM");
            TargetEntity target = resolveTargetEntity(
                    null, field.getRefEntityId());
            config.put(
                    "entityCode",
                    target == null ? null : target.entityCode());
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
        PageRequest page = PageRequest.normalize(pageNum, pageSize, 10, 100);
        int start = page.startIndex(total);
        int end = Math.min(start + page.pageSize(), total);
        List<Map<String, Object>> records = list.subList(start, end);
        
        // 只保留关键字段
        List<Map<String, Object>> simplified = records.stream()
                .map(this::simplifyEntityData)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("records", simplified);
        result.put("total", total);
        result.put("pageNum", page.pageNumber());
        result.put("pageSize", page.pageSize());
        
        return Result.success(result);
    }

    private Result<Map<String, Object>> selectSystemEntity(
            String entityCode,
            String keyword,
            Integer pageNum,
            Integer pageSize) {
        PageResult<EntityDataDTO> page =
                systemEntityReadService.findSelectorPage(
                        entityCode,
                        keyword,
                        pageNum == null ? 1 : pageNum,
                        pageSize == null ? 10 : pageSize);
        Map<String, Object> result = new HashMap<>();
        result.put(
                "records",
                page.getRecords().stream()
                        .map(this::simplifyEntityData)
                        .toList());
        result.put("total", page.getTotal());
        result.put("pageNum", page.getPageNum());
        result.put("pageSize", page.getPageSize());
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

    private Map<String, Object> simplifyEntityData(EntityDataDTO detail) {
        Map<String, Object> data = detail.getData() == null
                ? Map.of()
                : detail.getData();
        Map<String, Object> simplified = new HashMap<>();
        simplified.put("id", firstValue(detail.getId(), data.get("id")));
        simplified.put(
                "name",
                firstValue(detail.getName(), data.get("name")));
        simplified.put(
                "code",
                firstValue(detail.getCode(), data.get("code")));
        simplified.put(
                "dataNo",
                firstValue(
                        detail.getDataNo(),
                        data.get("dataNo"),
                        data.get("data_no")));
        simplified.put(
                "title",
                firstValue(detail.getTitle(), data.get("title")));
        simplified.put(
                "status",
                firstValue(detail.getStatus(), data.get("status")));
        simplified.put("entityType", "CUSTOM");
        return simplified;
    }

    private Object firstValue(Object... values) {
        for (Object value : values) {
            if (value != null
                    && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    private TargetEntity resolveTargetEntity(
            String entityCode,
            String refEntityId) {
        if (refEntityId != null && !refEntityId.isEmpty()) {
            try {
                EntityDefinitionDTO entity = entityDefinitionService.findById(refEntityId);
                if (entity != null) {
                    return new TargetEntity(
                            entity.getEntityCode(),
                            entity.getStorageMode()
                                    == EntityDefinition.StorageMode.SYSTEM);
                }
            } catch (Exception e) {
                // 忽略查询异常
            }
        }
        if (entityCode != null && !entityCode.isEmpty()) {
            return new TargetEntity(
                    entityCode,
                    systemEntityReadService.isSystemEntity(entityCode));
        }
        return null;
    }

    private record TargetEntity(
            String entityCode,
            boolean system) {
    }
}
