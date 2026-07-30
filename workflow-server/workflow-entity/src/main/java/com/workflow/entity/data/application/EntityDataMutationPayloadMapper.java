package com.workflow.entity.data.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把表单请求映射为动态实体存储字段，并过滤未发布字段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityDataMutationPayloadMapper {

    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityPublishedSnapshotService snapshotService;
    private final EntityDataMutationValidator validator;

    public Map<String, Object> buildUpdateData(
            String entityCode,
            String id,
            Map<String, Object> request,
            Map<String, Object> existingData) {
        Map<String, Object> updateData =
                new HashMap<>();
        updateData.put("id", id);
        updateData.put(
                "update_by",
                UserContext.getUserId());
        updateData.put(
                "update_time",
                LocalDateTime.now());
        copyStandardFields(
                request,
                updateData);

        Set<String> writableColumns =
                new HashSet<>(existingData.keySet());
        EntityPublishedSnapshot snapshot =
                snapshotService.getLatestByEntityCode(
                        entityCode);
        if (snapshot.getFields() != null) {
            snapshot.getFields().stream()
                    .filter(field ->
                            !validator.isRelationField(field))
                    .map(EntityField::getFieldCode)
                    .filter(StringUtils::hasText)
                    .map(recordMapper::toColumnName)
                    .forEach(writableColumns::add);
        }
        recordMapper.extractRequestCustomData(request)
                .forEach((column, value) -> {
                    if (writableColumns.contains(column)) {
                        updateData.put(column, value);
                    } else {
                        log.warn(
                                "忽略实体更新请求中的未发布字段: entityCode={}, field={}",
                                entityCode,
                                column);
                    }
                });
        existingData.forEach(
                updateData::putIfAbsent);
        return updateData;
    }

    @SuppressWarnings("unchecked")
    private void copyStandardFields(
            Map<String, Object> request,
            Map<String, Object> updateData) {
        Object nested = request.get("data");
        Map<String, Object> customData =
                nested instanceof Map<?, ?>
                        ? (Map<String, Object>) nested
                        : null;
        standardFieldMap().forEach(
                (frontendKey, dbKey) -> {
                    boolean present =
                            request.containsKey(frontendKey)
                                    || customData != null
                                    && customData.containsKey(
                                            frontendKey);
                    if (!present) {
                        return;
                    }
                    Object value =
                            request.containsKey(frontendKey)
                                    ? request.get(frontendKey)
                                    : customData.get(frontendKey);
                    if (value instanceof String text
                            && text.isEmpty()) {
                        value = null;
                    }
                    updateData.put(dbKey, value);
                });
    }

    private Map<String, String> standardFieldMap() {
        Map<String, String> values =
                new HashMap<>();
        values.put("name", "name");
        values.put("code", "code");
        values.put("status", "status");
        values.put("title", "title");
        values.put("dataNo", "data_no");
        values.put(
                "processInstanceId",
                "process_instance_id");
        values.put(
                "processStartTime",
                "process_start_time");
        values.put(
                "processEndTime",
                "process_end_time");
        values.put(
                "currentTaskId",
                "current_task_id");
        values.put(
                "currentTaskName",
                "current_task_name");
        values.put(
                "currentTaskAssignee",
                "current_task_assignee");
        values.put("submitterId", "submitter_id");
        values.put(
                "submitterName",
                "submitter_name");
        values.put("deptId", "dept_id");
        values.put("submitTime", "submit_time");
        return values;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestCustomData(
            Map<String, Object> request) {
        if (request == null) {
            return new HashMap<>();
        }
        Object nested = request.get("data");
        if (nested instanceof Map<?, ?> nestedMap) {
            return new HashMap<>(
                    (Map<String, Object>) nestedMap);
        }
        return new HashMap<>(request);
    }

    @SuppressWarnings("unchecked")
    public void removeFields(
            Map<String, Object> request,
            Set<String> fieldCodes) {
        if (request == null
                || fieldCodes == null
                || fieldCodes.isEmpty()) {
            return;
        }
        fieldCodes.forEach(request::remove);
        Object nested = request.get("data");
        if (nested instanceof Map<?, ?> nestedMap) {
            Map<String, Object> customData =
                    (Map<String, Object>) nestedMap;
            fieldCodes.forEach(customData::remove);
        }
    }

    public EntityDataDTO toRuntimeDto(
            Map<String, Object> data,
            String entityCode) {
        return recordMapper.toDto(
                data,
                entityCode,
                runtimeFields(entityCode));
    }

    private List<EntityField> runtimeFields(
            String entityCode) {
        try {
            EntityPublishedSnapshot snapshot =
                    snapshotService.getLatestByEntityCode(
                            entityCode);
            return snapshot.getFields() == null
                    ? List.of()
                    : snapshot.getFields();
        } catch (RuntimeException exception) {
            log.debug(
                    "读取实体发布字段失败，使用兼容字段映射: entityCode={}, reason={}",
                    entityCode,
                    exception.getMessage());
            return List.of();
        }
    }
}
