package com.workflow.entity.data.application.mapping;

import com.workflow.core.logging.LogValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
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
            "id", "name", "code", "status", "processStatus", "process_status",
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
            "create_time", "update_time", "create_by", "update_by",
            "deleted", "entityCode", "entity_code", "entityName", "entity_name",
            "deptName", "dept_name", "startProcess", "start_process",
            "listKey", "list_key", "data",
            "processVariables", "process_variables",
            "extData", "ext_data",
            "actionCapabilities", "action_capabilities"));

    /** 存储层（动态表）系统列集合，提取自定义字段时需排除这些列 */
    private static final Set<String> STORAGE_SYSTEM_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "name", "code", "status", "processStatus", "process_status",
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
        return toDto(data, entityCode, List.of());
    }

    /**
     * 将动态表存储行映射为实体数据 DTO，并按实体定义还原精确字段编码。
     *
     * @param data       动态表行数据（列名 -> 值）
     * @param entityCode 实体编码
     * @param fields     当前实体字段定义
     * @return 实体数据 DTO
     */
    public EntityDataDTO toDto(
            Map<String, Object> data,
            String entityCode,
            Collection<EntityField> fields) {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId(getString(data, "id"));
        dto.setEntityCode(entityCode);
        dto.setName(getString(data, "name"));
        dto.setCode(getString(data, "code"));
        dto.setStatus(getString(data, "status"));
        dto.setProcessInstanceId(getString(data, "process_instance_id"));
        dto.setProcessStatus(getString(data, "process_status"));
        dto.setProcessStartTime(getDateTime(data, "process_start_time"));
        dto.setProcessEndTime(getDateTime(data, "process_end_time"));
        dto.setCurrentTaskId(getString(data, "current_task_id"));
        dto.setCurrentTaskName(getString(data, "current_task_name"));
        dto.setCurrentTaskAssignee(getString(data, "current_task_assignee"));
        dto.setSubmitterId(getString(data, "submitter_id"));
        dto.setSubmitterName(getString(data, "submitter_name"));
        dto.setDeptId(getString(data, "dept_id"));
        dto.setSubmitTime(getDateTime(data, "submit_time"));
        dto.setCreateTime(getDateTime(data, "create_time"));
        dto.setUpdateTime(getDateTime(data, "update_time"));
        dto.setCreateBy(getString(data, "create_by"));
        dto.setUpdateBy(getString(data, "update_by"));
        Object deleted = data.get("deleted");
        // MySQL tinyint 的驱动返回值可能是 Boolean 或数值，统一为表单布尔值。
        dto.setDeleted(deleted == null ? null
                : Boolean.TRUE.equals(deleted) || "1".equals(String.valueOf(deleted)));
        dto.setData(extractCustomFields(data, fields));
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
     * 将字段编码转换为物理列名；下划线编码原样保留，驼峰编码转为下划线。
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

    /**
     * 提取自定义字段；输出作为后续校验或处理的输入。
     *
     * @param data 数据，后续用于提取自定义字段并传递处理结果
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 自定义字段键值结果，供调用方继续处理
     */
    private Map<String, Object> extractCustomFields(
            Map<String, Object> data,
            Collection<EntityField> fields) {
        Map<String, Object> customData = new HashMap<>();
        Map<String, String> fieldCodeByColumn = new HashMap<>();
        if (fields != null) {
            for (EntityField field : fields) {
                if (field == null || !StringUtils.hasText(field.getFieldCode())) {
                    continue;
                }
                String columnName = StringUtils.hasText(field.getDbColumnName())
                        ? field.getDbColumnName()
                        : toColumnName(field.getFieldCode());
                fieldCodeByColumn.put(columnName, field.getFieldCode());
            }
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!STORAGE_SYSTEM_COLUMNS.contains(entry.getKey())) {
                String fieldCode = fieldCodeByColumn.getOrDefault(
                        entry.getKey(),
                        underscoreToCamel(entry.getKey()));
                customData.put(fieldCode, parseJsonValue(entry.getValue()));
            }
        }
        return customData;
    }

    /**
     * 解析JSON值；输出作为后续校验或处理的输入。
     *
     * @param value 待解析JSON值的原始输入，结果供调用方继续使用
     * @return 解析后的JSON值结果，供调用方继续处理
     */
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

    /**
     * 规范化值；输出作为后续校验或处理的输入。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param value 待规范化值的原始输入，结果供调用方继续使用
     * @return 规范化后的值结果，供调用方继续处理
     */
    private Object normalizeValue(String key, Object value) {
        if (value instanceof String str && str.isEmpty()) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                log.warn("字段 {} 序列化 JSON 失败: failureType={}",
                        LogValue.safe(key), LogValue.failureType(e));
            }
        }
        return value;
    }

    /**
     * 生成{@code underscore}截止{@code camel}文本，供后续匹配或展示。
     *
     * @param underscore {@code underscore}，供本方法处理{@code underscore}截止{@code camel}时使用
     * @return 处理后的{@code underscore}截止{@code camel}文本，供调用方比较或展示
     */
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

    /**
     * 读取字符串；查询结果供调用方展示或继续处理。
     *
     * @param data 数据，后续用于读取字符串并传递处理结果
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 读取后的字符串文本，供调用方比较或展示
     */
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 读取日期时间；查询结果供调用方展示或继续处理。
     *
     * @param data 数据，后续用于读取日期时间并传递处理结果
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的本地日期时间结果，供调用方继续处理
     */
    private LocalDateTime getDateTime(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return null;
    }

    /**
     * 写入条件非空值；后续读取或执行将使用更新后的状态。
     *
     * @param data 数据，后续用于写入条件非空值并传递处理结果
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param value 待写入条件非空值的原始输入，结果供调用方继续使用
     */
    private void putIfNotNull(Map<String, Object> data, String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
