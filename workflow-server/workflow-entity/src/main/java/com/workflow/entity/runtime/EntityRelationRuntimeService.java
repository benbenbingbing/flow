package com.workflow.entity.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityRelation;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.EntityCodeGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 动态实体关系运行时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityRelationRuntimeService {

    private static final int MAX_RELATION_DEPTH = 8;

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final DynamicTableService dynamicTableService;
    private final ObjectMapper objectMapper;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityCodeGeneratorService codeGeneratorService;

    /**
     * 加载实体配置的所有关系定义。
     *
     * @param definition 实体定义
     * @return 关系定义列表（无则返回空列表）
     */
    public List<EntityRelation> loadRelations(EntityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return List.of();
        }
        List<EntityRelation> relations = relationMapper.selectByParentEntityId(definition.getId());
        return relations != null ? relations : List.of();
    }

    /**
     * 从数据中剔除关系字段，返回仅含父表字段的数据副本。
     *
     * @param data       实体数据
     * @param relations 关系定义列表
     * @return 剔除关系字段后的数据副本
     */
    public Map<String, Object> withoutRelationData(Map<String, Object> data, List<EntityRelation> relations) {
        if (data == null || data.isEmpty() || relations == null || relations.isEmpty()) {
            return data;
        }
        Map<String, Object> parentData = new HashMap<>(data);
        for (EntityRelation relation : relations) {
            if (StringUtils.hasText(relation.getParentFieldCode())) {
                parentData.remove(relation.getParentFieldCode());
            }
        }
        return parentData;
    }

    /**
     * 从表单提交数据中剔除关系字段，兼容 data 子对象嵌套结构。
     *
     * @param formData 表单提交数据
     * @param relations 关系定义列表
     * @return 剔除关系字段后的表单数据副本
     */
    public Map<String, Object> withoutRelationDataFromRequest(Map<String, Object> formData, List<EntityRelation> relations) {
        if (formData == null || formData.isEmpty()) {
            return formData;
        }
        Map<String, Object> parentFormData = new HashMap<>(formData);
        Object dataObj = formData.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) dataObj;
            parentFormData.put("data", withoutRelationData(customData, relations));
        } else {
            parentFormData = withoutRelationData(parentFormData, relations);
        }
        return parentFormData;
    }

    /**
     * 从数据中抽取关系字段取值。
     *
     * @param data       实体数据
     * @param relations 关系定义列表
     * @return 关系字段编码到取值的映射
     */
    public Map<String, Object> extractRelationData(Map<String, Object> data, List<EntityRelation> relations) {
        Map<String, Object> result = new HashMap<>();
        if (data == null || data.isEmpty() || relations == null || relations.isEmpty()) {
            return result;
        }
        for (EntityRelation relation : relations) {
            String fieldCode = relation.getParentFieldCode();
            if (StringUtils.hasText(fieldCode) && data.containsKey(fieldCode)) {
                result.put(fieldCode, data.get(fieldCode));
            }
        }
        return result;
    }

    /**
     * 从表单提交数据中抽取关系字段取值，兼容 data 子对象嵌套结构。
     *
     * @param formData 表单提交数据
     * @param relations 关系定义列表
     * @return 关系字段编码到取值的映射
     */
    public Map<String, Object> extractRelationDataFromRequest(Map<String, Object> formData, List<EntityRelation> relations) {
        if (formData == null) {
            return Map.of();
        }
        Object dataObj = formData.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) dataObj;
            return extractRelationData(customData, relations);
        }
        return extractRelationData(formData, relations);
    }

    /**
     * 递归保存父记录下所有关系子数据：新增/更新传入行，删除未传入的旧行。
     *
     * @param parentId      父记录ID
     * @param relations      关系定义列表
     * @param relationData   关系字段取值（可含多级嵌套子数据）
     */
    public void saveRelationData(String parentId, List<EntityRelation> relations, Map<String, Object> relationData) {
        saveRelationData(parentId, relations, relationData, 1, new HashSet<>());
    }

    /**
     * 递归加载实体数据 DTO 的所有关系子数据并回填到 data 中。
     * 一对一关系填充单个对象，一对多关系填充列表。
     *
     * @param dto 实体数据 DTO
     */
    public void loadRelationData(EntityDataDTO dto) {
        if (dto == null || dto.getId() == null || dto.getEntityCode() == null) {
            return;
        }
        EntityDefinition definition = definitionMapper.findByEntityCode(dto.getEntityCode()).orElse(null);
        if (definition == null) {
            return;
        }
        if (dto.getData() == null) {
            dto.setData(new HashMap<>());
        }
        loadRelationData(definition, dto.getId(), dto.getData(), 1, new HashSet<>());
    }

    /**
     * 级联删除父记录下所有标记为级联删除的子记录（递归向下）。
     *
     * @param parentDefinition 父实体定义
     * @param parentId          父记录ID
     * @param physical          true-物理删除 false-逻辑删除
     */
    public void cascadeDeleteRelations(EntityDefinition parentDefinition, String parentId, boolean physical) {
        cascadeDeleteRelations(parentDefinition, parentId, physical, 1, new HashSet<>());
    }

    private void saveRelationData(String parentId, List<EntityRelation> relations, Map<String, Object> relationData,
                                  int depth, Set<String> path) {
        if (!StringUtils.hasText(parentId) || relations == null || relations.isEmpty()
                || relationData == null || relationData.isEmpty() || depth > MAX_RELATION_DEPTH) {
            return;
        }
        for (EntityRelation relation : relations) {
            String fieldCode = relation.getParentFieldCode();
            if (!StringUtils.hasText(fieldCode) || !relationData.containsKey(fieldCode)) {
                continue;
            }
            Object relationValue = relationData.get(fieldCode);
            if (relationValue == null) {
                // 前端未提供该关系字段数据时，跳过处理，避免误删已有子表数据
                continue;
            }

            String pathKey = relation.getParentEntityCode() + ":" + fieldCode;
            if (!path.add(pathKey)) {
                log.warn("关系存在循环，跳过: {}", pathKey);
                continue;
            }

            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())) {
                path.remove(pathKey);
                continue;
            }

            ensureEntityTable(childDefinition);
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> existingRows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            List<Map<String, Object>> incomingRows = toRelationRows(relationValue, relation.getRelationType());
            Set<String> activeIds = new HashSet<>();

            List<EntityRelation> childRelations = loadRelations(childDefinition);
            for (Map<String, Object> row : incomingRows) {
                Map<String, Object> childRelationData = extractRelationData(row, childRelations);
                Map<String, Object> childData = withoutRelationData(row, childRelations);
                childData.put(relation.getChildRefFieldCode(), parentId);
                childData.put("update_by", UserContext.getUserId());
                childData.put("update_time", LocalDateTime.now());
                childData.put("deleted", 0);
                normalizeJsonValues(childData);

                String childId = stringValue(childData.get("id"));
                boolean isNewChild = !StringUtils.hasText(childId);
                if (isNewChild) {
                    childId = generateId();
                    childData.put("id", childId);
                    childData.put("create_by", UserContext.getUserId());
                    childData.put("create_time", LocalDateTime.now());
                }

                // 新增子行未填写编码时，自动生成编码（与主表行为保持一致，未配置规则时会自动创建默认规则）
                if (isNewChild) {
                    Object existingCode = childData.get("code");
                    if (existingCode == null || existingCode.toString().trim().isEmpty()) {
                        childData.put("code", codeGeneratorService.generateCode(childDefinition.getEntityCode()));
                    }
                }

                if (isNewChild) {
                    dynamicMapper.insert(childTableName, childData);
                } else {
                    dynamicMapper.update(childTableName, childData);
                }
                activeIds.add(childId);
                saveRelationData(childId, childRelations, childRelationData, depth + 1, path);
            }

            deleteMissingRows(childTableName, existingRows, activeIds);
            path.remove(pathKey);
        }
    }

    private void loadRelationData(EntityDefinition parentDefinition, String parentId, Map<String, Object> target,
                                  int depth, Set<String> path) {
        if (parentDefinition == null || !StringUtils.hasText(parentId) || target == null || depth > MAX_RELATION_DEPTH) {
            return;
        }
        List<EntityRelation> relations = loadRelations(parentDefinition);
        for (EntityRelation relation : relations) {
            String fieldCode = relation.getParentFieldCode();
            if (!StringUtils.hasText(fieldCode)) {
                continue;
            }

            String pathKey = relation.getParentEntityCode() + ":" + fieldCode;
            if (!path.add(pathKey)) {
                continue;
            }

            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())) {
                path.remove(pathKey);
                continue;
            }

            if (!dynamicTableService.tableExists(childDefinition.getEntityCode())) {
                target.put(fieldCode, emptyRelationValue(relation));
                path.remove(pathKey);
                continue;
            }
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> rows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            List<Map<String, Object>> childRows = rows == null ? List.of() : rows.stream()
                    .map(row -> {
                        Map<String, Object> childRow = toChildFormRow(row, childDefinition.getEntityCode());
                        String childId = stringValue(childRow.get("id"));
                        loadRelationData(childDefinition, childId, childRow, depth + 1, path);
                        return childRow;
                    })
                    .collect(Collectors.toList());
            if (relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE) {
                target.put(fieldCode, childRows.isEmpty() ? null : childRows.get(0));
            } else {
                target.put(fieldCode, childRows);
            }
            path.remove(pathKey);
        }
    }

    private void cascadeDeleteRelations(EntityDefinition parentDefinition, String parentId, boolean physical,
                                        int depth, Set<String> path) {
        if (parentDefinition == null || !StringUtils.hasText(parentId) || depth > MAX_RELATION_DEPTH) {
            return;
        }
        for (EntityRelation relation : loadRelations(parentDefinition)) {
            if (!Boolean.TRUE.equals(relation.getCascadeDelete()) || !StringUtils.hasText(relation.getParentFieldCode())) {
                continue;
            }
            String pathKey = relation.getParentEntityCode() + ":" + relation.getParentFieldCode();
            if (!path.add(pathKey)) {
                continue;
            }
            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())
                    || !dynamicTableService.tableExists(childDefinition.getEntityCode())) {
                path.remove(pathKey);
                continue;
            }
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> childRows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            for (Map<String, Object> childRow : childRows) {
                String childId = stringValue(childRow.get("id"));
                if (!StringUtils.hasText(childId)) {
                    continue;
                }
                cascadeDeleteRelations(childDefinition, childId, physical, depth + 1, path);
                if (physical) {
                    dynamicMapper.physicalDeleteById(childTableName, childId);
                } else {
                    dynamicMapper.deleteById(childTableName, childId);
                }
            }
            path.remove(pathKey);
        }
    }

    private EntityDefinition loadChildEntity(EntityRelation relation) {
        EntityDefinition childDefinition = null;
        if (StringUtils.hasText(relation.getChildEntityId())) {
            childDefinition = definitionMapper.selectById(relation.getChildEntityId());
        }
        if (childDefinition == null && StringUtils.hasText(relation.getChildEntityCode())) {
            childDefinition = definitionMapper.findByEntityCode(relation.getChildEntityCode()).orElse(null);
        }
        if (childDefinition != null) {
            childDefinition.setFields(loadEntityFields(childDefinition));
        }
        return childDefinition;
    }

    private List<EntityField> loadEntityFields(EntityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return List.of();
        }
        List<EntityField> fields = fieldMapper.findByEntityId(definition.getId());
        return fields != null ? fields : List.of();
    }

    private void ensureEntityTable(EntityDefinition definition) {
        if (!dynamicTableService.tableExists(definition.getEntityCode())) {
            dynamicTableService.createEntityTable(definition);
        }
    }

    private List<Map<String, Object>> toRelationRows(Object value, EntityRelation.RelationType relationType) {
        if (value == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) value;
            rows.add(new HashMap<>(row));
            return rows;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) item;
                    rows.add(new HashMap<>(row));
                }
            }
        }
        if (relationType == EntityRelation.RelationType.ONE_TO_ONE && rows.size() > 1) {
            return List.of(rows.get(0));
        }
        return rows;
    }

    private List<Map<String, Object>> findRowsByReference(String tableName, String refFieldCode, String parentId) {
        Map<String, Object> condition = new HashMap<>();
        condition.put(refFieldCode, parentId);
        condition.put(refFieldCode + "_op", "EQ");
        List<Map<String, Object>> rows = dynamicMapper.selectByCondition(tableName, condition);
        return rows != null ? rows : List.of();
    }

    private void deleteMissingRows(String tableName, List<Map<String, Object>> existingRows, Set<String> activeIds) {
        if (existingRows == null || existingRows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : existingRows) {
            String id = stringValue(row.get("id"));
            if (StringUtils.hasText(id) && !activeIds.contains(id)) {
                dynamicMapper.deleteById(tableName, id);
            }
        }
    }

    private Object emptyRelationValue(EntityRelation relation) {
        return relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE ? null : List.of();
    }

    private Map<String, Object> toChildFormRow(Map<String, Object> row, String entityCode) {
        EntityDataDTO childDto = recordMapper.toDto(row, entityCode);
        Map<String, Object> data = new HashMap<>();
        if (childDto.getData() != null) {
            data.putAll(childDto.getData());
        }
        // 把常用系统字段也合并到子表单数据，确保表单字段（如名称、编码等）能正确回显
        putIfPresent(data, "id", childDto.getId());
        putIfPresent(data, "name", childDto.getName());
        putIfPresent(data, "code", childDto.getCode());
        putIfPresent(data, "title", childDto.getTitle());
        putIfPresent(data, "dataNo", childDto.getDataNo());
        putIfPresent(data, "status", childDto.getStatus());
        putIfPresent(data, "deptId", childDto.getDeptId());
        putIfPresent(data, "submitterId", childDto.getSubmitterId());
        putIfPresent(data, "submitterName", childDto.getSubmitterName());
        return data;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private void normalizeJsonValues(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map || value instanceof List) {
                try {
                    entry.setValue(objectMapper.writeValueAsString(value));
                } catch (Exception e) {
                    log.warn("字段 {} 序列化 JSON 失败: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
