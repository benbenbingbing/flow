package com.workflow.entity.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态实体运行时记录映射。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityRuntimeRecordMapper {

    /** DTO 层系统字段集合（含驼峰与下划线形式），这些字段不视为自定义业务字段 */
    private static final Set<String> DTO_SYSTEM_FIELDS = new HashSet<>(Arrays.asList(
            "id", "dataNo", "data_no", "title", "name", "code", "status",
            "processInstanceId", "process_instance_id",
            "processStartTime", "process_start_time",
            "processEndTime", "process_end_time",
            "currentTaskId", "current_task_id",
            "currentTaskName", "current_task_name",
            "currentTaskAssignee", "current_task_assignee",
            "submitterId", "submitter_id",
            "submitterName", "submitter_name",
            "deptId", "dept_id",
            "submitTime", "submit_time",
            "createdTime", "create_time", "updatedTime", "update_time",
            "createdBy", "createBy", "create_by", "updatedBy", "updateBy", "update_by",
            "deleted", "entityCode", "entity_code", "startProcess", "start_process"));

    /** 存储层（动态表）系统列集合，提取自定义字段时需排除这些列 */
    private static final Set<String> STORAGE_SYSTEM_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "data_no", "title", "name", "code", "status",
            "process_instance_id", "process_start_time", "process_end_time",
            "current_task_id", "current_task_name", "current_task_assignee",
            "submitter_id", "submitter_name", "dept_id", "submit_time",
            "create_time", "update_time", "create_by", "update_by", "deleted"));

    private final ObjectMapper objectMapper;

    /**
     * 将动态表存储行映射为实体数据 DTO。
     * 系统列直接填充 DTO 标准字段，其余列转为自定义字段数据。
     *
     * @param data       动态表行数据（列名 -> 值）
     * @param entityCode 实体编码
     * @return 实体数据 DTO
     */
    public EntityDataDTO toDto(Map<String, Object> data, String entityCode) {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId(getString(data, "id"));
        dto.setEntityCode(entityCode);
        dto.setDataNo(getString(data, "data_no"));
        dto.setTitle(getString(data, "title"));
        dto.setName(getString(data, "name"));
        dto.setCode(getString(data, "code"));
        dto.setStatus(getString(data, "status"));
        dto.setProcessInstanceId(getString(data, "process_instance_id"));
        dto.setProcessStartTime(getDateTime(data, "process_start_time"));
        dto.setProcessEndTime(getDateTime(data, "process_end_time"));
        dto.setCurrentTaskId(getString(data, "current_task_id"));
        dto.setCurrentTaskName(getString(data, "current_task_name"));
        dto.setCurrentTaskAssignee(getString(data, "current_task_assignee"));
        dto.setSubmitterId(getString(data, "submitter_id"));
        dto.setSubmitterName(getString(data, "submitter_name"));
        dto.setDeptId(getString(data, "dept_id"));
        dto.setSubmitTime(getDateTime(data, "submit_time"));
        dto.setCreatedAt(getDateTime(data, "create_time"));
        dto.setUpdatedAt(getDateTime(data, "update_time"));
        dto.setCreatedBy(getString(data, "create_by"));
        dto.setUpdatedBy(getString(data, "update_by"));
        dto.setData(extractCustomFields(data));
        dto.setExtData(new HashMap<>());
        return dto;
    }

    /**
     * 将实体数据 DTO 转换为可写入动态表的存储 Map。
     * 系统字段写入固定列，自定义字段按驼峰转下划线后写入对应列。
     *
     * @param dto 实体数据 DTO
     * @return 动态表存储 Map（列名 -> 值）
     */
    public Map<String, Object> toStorageMap(EntityDataDTO dto) {
        Map<String, Object> data = new HashMap<>();

        putIfNotNull(data, "id", dto.getId());
        putIfNotNull(data, "data_no", dto.getDataNo());
        putIfNotNull(data, "title", dto.getTitle());
        putIfNotNull(data, "name", dto.getName());
        putIfNotNull(data, "code", dto.getCode());
        putIfNotNull(data, "status", dto.getStatus());
        putIfNotNull(data, "process_instance_id", dto.getProcessInstanceId());
        putIfNotNull(data, "process_start_time", dto.getProcessStartTime());
        putIfNotNull(data, "process_end_time", dto.getProcessEndTime());
        putIfNotNull(data, "current_task_id", dto.getCurrentTaskId());
        putIfNotNull(data, "current_task_name", dto.getCurrentTaskName());
        putIfNotNull(data, "current_task_assignee", dto.getCurrentTaskAssignee());
        putIfNotNull(data, "submitter_id", dto.getSubmitterId());
        putIfNotNull(data, "submitter_name", dto.getSubmitterName());
        putIfNotNull(data, "dept_id", dto.getDeptId());
        putIfNotNull(data, "submit_time", dto.getSubmitTime());

        if (dto.getData() != null) {
            for (Map.Entry<String, Object> entry : dto.getData().entrySet()) {
                String key = entry.getKey();
                if (!isCustomField(key)) {
                    continue;
                }
                data.put(toColumnName(key), normalizeValue(key, entry.getValue()));
            }
        }

        return data;
    }

    /**
     * 从表单提交数据中抽取自定义业务字段（剔除系统字段）。
     *
     * @param formData 表单提交数据（可能包含 data 子对象）
     * @return 自定义字段 Map（下划线列名 -> 值）
     */
    public Map<String, Object> extractRequestCustomData(Map<String, Object> formData) {
        Map<String, Object> result = new HashMap<>();
        if (formData == null) {
            return result;
        }
        Object dataObj = formData.get("data");
        if (!(dataObj instanceof Map)) {
            return result;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> customData = (Map<String, Object>) dataObj;
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            String key = entry.getKey();
            if (!isCustomField(key)) {
                continue;
            }
            String columnName = toColumnName(key);
            if (DTO_SYSTEM_FIELDS.contains(columnName)) {
                continue;
            }
            result.put(columnName, normalizeValue(key, entry.getValue()));
        }
        return result;
    }

    /**
     * 判断给定字段名是否为自定义业务字段（非空且不属于系统字段）。
     *
     * @param fieldName 字段名
     * @return true 表示自定义业务字段
     */
    public boolean isCustomField(String fieldName) {
        return fieldName != null
                && !fieldName.isEmpty()
                && !"undefined".equals(fieldName)
                && !"null".equals(fieldName)
                && !DTO_SYSTEM_FIELDS.contains(fieldName);
    }

    /**
     * 将驼峰命名字段名转换为下划线数据库列名（如 userName -> user_name）。
     *
     * @param fieldName 字段名
     * @return 下划线列名
     */
    public String toColumnName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append("_").append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private Map<String, Object> extractCustomFields(Map<String, Object> data) {
        Map<String, Object> customData = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!STORAGE_SYSTEM_COLUMNS.contains(entry.getKey())) {
                customData.put(underscoreToCamel(entry.getKey()), parseJsonValue(entry.getValue()));
            }
        }
        return customData;
    }

    private Object parseJsonValue(Object value) {
        if (!(value instanceof String str)) {
            return value;
        }
        String trimmed = str.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return value;
        }
        try {
            return objectMapper.readValue(trimmed, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    private Object normalizeValue(String key, Object value) {
        if (value instanceof String str && str.isEmpty()) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                log.warn("字段 {} 序列化 JSON 失败: {}", key, e.getMessage());
            }
        }
        return value;
    }

    private String underscoreToCamel(String underscore) {
        if (underscore == null || underscore.isEmpty()) {
            return underscore;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < underscore.length(); i++) {
            char c = underscore.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                result.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private LocalDateTime getDateTime(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return null;
    }

    private void putIfNotNull(Map<String, Object> data, String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
