package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntityLookupEventReader {

    private final EntityActionCapabilityService permissions;
    private final EntityDataDynamicService dynamicRecords;
    private final SystemEntityReadService systemRecords;
    private final EntityPublishedSnapshotService publishedEntities;
    private final ObjectMapper objectMapper;

    /** 核实关闭事件的记录坐标，沿用实体详情权限，只返回授权结果。 */
    public void requireAccessibleRecord(String entityCode, String id, String listKey) {
        if (systemRecords.isSystemEntity(entityCode)) {
            systemRecords.findById(entityCode, id);
        } else {
            permissions.requireStandardPermission(entityCode, EntityPermissionAction.VIEW);
            dynamicRecords.requireAccessibleById(entityCode, id, listKey);
        }
    }

    /**
     * 使用当前用户读取目标实体的主记录。返回 null 同时表示记录不存在和不可见，
     * 输出仅包含请求投影且已发布可读的属性，供事件进行回填或继续映射。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> read(String entityCode, String id, Set<String> fields) {
        boolean system = systemRecords.isSystemEntity(entityCode);
        EntityDataDTO row;
        Set<String> publishedFields;
        try {
            if (system) {
                // 系统实体详情读取已在后端应用受支持实体及可读列白名单。
                row = systemRecords.findVisibleById(entityCode, id);
                if (row == null) return null;
                publishedFields = row.getData().keySet();
            } else {
                permissions.requireStandardPermission(entityCode, EntityPermissionAction.VIEW);
                var snapshot = publishedEntities.findLatestByEntityCode(entityCode);
                if (snapshot == null) return null;
                publishedFields = snapshot.getFields().stream()
                        .filter(field -> field.getFieldType() != EntityField.FieldType.SUB_FORM
                                && field.getFieldType() != EntityField.FieldType.SUB_LIST)
                        .map(EntityField::getFieldCode)
                        .collect(Collectors.toSet());
                row = dynamicRecords.findVisibleMainById(entityCode, id);
            }
        } catch (ForbiddenException unavailable) {
            return null;
        }
        if (row == null) return null;

        Map<String, Object> data = new LinkedHashMap<>();
        for (String field : fields) {
            if ("name".equals(field) && !system) data.put(field, row.getName());
            else if ("code".equals(field) && !system) data.put(field, row.getCode());
            else if (publishedFields.contains(field) && row.getData().containsKey(field)) {
                data.put(field, row.getData().get(field));
            }
        }
        // 复用详情字段序列化，避免大整数或高精度小数经过浏览器时丢失精度。
        EntityDataDTO projection = new EntityDataDTO();
        projection.setData(data);
        JsonNode serialized = objectMapper.<JsonNode>valueToTree(projection).path("data");
        Map<String, Object> wireData = serialized.isObject()
                ? objectMapper.convertValue(serialized, new TypeReference<LinkedHashMap<String, Object>>() {})
                : new LinkedHashMap<>();
        data.forEach((field, value) -> { if (value == null) wireData.putIfAbsent(field, null); });
        return Map.of("id", row.getId(), "data", wireData);
    }
}
