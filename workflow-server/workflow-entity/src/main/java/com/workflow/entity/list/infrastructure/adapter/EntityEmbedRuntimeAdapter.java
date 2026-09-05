package com.workflow.entity.list.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedRuntimeEntityPort;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.api.response.EntityListSchemaDTO;
import com.workflow.entity.list.application.EntityListRuntimeService;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.StringUtils;

/**
 * 将现有 Entity List Runtime 适配为 Embed 的稳定读取端口。
 *
 * <p>本适配器只做模型转换，发布版本固定、列表权限、Flow 用户数据范围和行级能力仍由
 * {@link EntityListRuntimeService} 统一执行。</p>
 */
@Component
public class EntityEmbedRuntimeAdapter implements EmbedRuntimeEntityPort {

    private final EntityListRuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    public EntityEmbedRuntimeAdapter(
            EntityListRuntimeService runtimeService,
            ObjectMapper objectMapper) {
        this.runtimeService = runtimeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ListSchema loadListSchema(
            String entityCode,
            String listKey,
            String listReleaseId,
            int listReleaseVersion) {
        EntityListSchemaDTO schema = runtimeService.schemaPinned(
                entityCode, listKey, listReleaseId, listReleaseVersion);

        List<Field> fields = new ArrayList<>();
        for (Object value : schema.getFields() == null ? List.of() : schema.getFields()) {
            EntityListField field = objectMapper.convertValue(value, EntityListField.class);
            fields.add(new Field(
                    field.getFieldCode(),
                    StringUtils.hasText(field.getFieldName())
                            ? field.getFieldName() : field.getFieldCode(),
                    publishedType(field),
                    field.getWidth(),
                    Boolean.TRUE.equals(field.getShowInList()),
                    Boolean.TRUE.equals(field.getIsQuery()),
                    normalizeOperator(field.getQueryType()),
                    options(field)));
        }
        return new ListSchema(
                schema.getEntityCode(),
                schema.getEntityName(),
                schema.getListKey(),
                schema.getListName(),
                immutableMap(schema.getSelectionConfig()),
                List.copyOf(fields),
                actions(schema.getToolbarConfig(), "TOOLBAR"),
                actions(schema.getRowActionConfig(), "ROW"));
    }

    @Override
    public ListPage queryList(
            String entityCode,
            String listKey,
            String listReleaseId,
            int listReleaseVersion,
            int pageNum,
            int pageSize,
            Map<String, Object> encodedClientFilters,
            Map<String, Object> trustedContextFilters) {
        ListSchema schema = loadListSchema(
                entityCode, listKey, listReleaseId, listReleaseVersion);
        Object result = runtimeService.queryPinned(
                entityCode,
                listKey,
                listReleaseId,
                listReleaseVersion,
                pageNum,
                pageSize,
                encodedClientFilters,
                trustedContextFilters);
        if (!(result instanceof PageResult<?> page)) {
            throw new IllegalStateException("固定列表查询没有返回分页结果");
        }
        List<String> publishedFieldCodes = schema.fields().stream()
                .map(Field::code)
                .filter(StringUtils::hasText)
                .toList();
        List<Row> rows = (page.getRecords() == null ? List.of() : page.getRecords())
                .stream()
                .map(record -> row(record, publishedFieldCodes))
                .toList();
        return new ListPage(
                rows,
                page.getTotal(),
                Math.toIntExact(page.getPageNum()),
                Math.toIntExact(page.getPageSize()));
    }

    private Row row(Object source, List<String> publishedFieldCodes) {
        Map<String, Object> object;
        Map<String, Object> data;
        Map<String, Object> extData;
        BeanWrapper bean = null;
        if (source instanceof EntityDataDTO dto) {
            object = Collections.singletonMap("id", dto.getId());
            data = dto.getData() == null ? Map.of() : dto.getData();
            extData = dto.getExtData() == null ? Map.of() : dto.getExtData();
            bean = new BeanWrapperImpl(dto);
        } else {
            object = objectMapper.convertValue(
                    source, new TypeReference<Map<String, Object>>() {});
            data = childMap(object.get("data"));
            extData = childMap(object.get("extData"));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String code : publishedFieldCodes) {
            if ("id".equals(code)) {
                continue;
            }
            if (object.containsKey(code)) {
                values.put(code, object.get(code));
            } else if (data.containsKey(code)) {
                values.put(code, data.get(code));
            } else if (extData.containsKey(code)) {
                values.put(code, extData.get(code));
            } else if (bean != null && bean.isReadableProperty(code)) {
                values.put(code, bean.getPropertyValue(code));
            }
        }
        Instant updatedAt = null;
        if (source instanceof EntityDataDTO dto && dto.getUpdatedAt() != null) {
            updatedAt = dto.getUpdatedAt().toInstant(ZoneOffset.UTC);
        }
        return new Row(
                Objects.toString(object.get("id"), null),
                Collections.unmodifiableMap(values),
                updatedAt,
                actionCapabilities(source, object));
    }

    private Map<String, ActionCapability> actionCapabilities(
            Object source,
            Map<String, Object> object) {
        Map<String, EntityActionCapabilityDTO> capabilities;
        if (source instanceof EntityDataDTO dto) {
            capabilities = dto.getActionCapabilities();
        } else {
            Object raw = object.get("actionCapabilities");
            capabilities = raw == null ? Map.of() : objectMapper.convertValue(
                    raw,
                    new TypeReference<Map<String, EntityActionCapabilityDTO>>() {});
        }
        Map<String, ActionCapability> result = new LinkedHashMap<>();
        if (capabilities != null) {
            capabilities.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null) {
                    result.put(key, new ActionCapability(
                            value.isVisible(),
                            value.isEnabled(),
                            value.getReason()));
                }
            });
        }
        return Map.copyOf(result);
    }

    private List<Action> actions(List<Map<String, Object>> values, String placement) {
        if (values == null) {
            return List.of();
        }
        List<Action> result = new ArrayList<>();
        for (Map<String, Object> value : values) {
            String key = text(value.get("key"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String label = text(value.get("label"));
            result.add(new Action(key, StringUtils.hasText(label) ? label : key, placement));
        }
        return List.copyOf(result);
    }

    private List<Option> options(EntityListField field) {
        try {
            JsonNode query = readConfig(field.getQueryConfig());
            JsonNode column = readConfig(field.getColumnConfig());
            JsonNode node = query.path("options").isArray()
                    ? query.path("options") : column.path("options");
            if (!node.isArray()) {
                return List.of();
            }
            List<Option> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isObject() || !item.hasNonNull("value")) {
                    continue;
                }
                String label = item.path("label").asText(item.path("value").asText());
                result.add(new Option(label, objectMapper.convertValue(
                        item.get("value"), Object.class)));
            }
            return List.copyOf(result);
        } catch (Exception ignored) {
            // 非法选项元数据不应扩大外部 Schema；降级为空选项并保持字段本身可显示。
            return List.of();
        }
    }

    /** Field type must come from the immutable list release, never mutable entity metadata. */
    private String publishedType(EntityListField field) {
        JsonNode column = readConfig(field.getColumnConfig());
        JsonNode query = readConfig(field.getQueryConfig());
        for (JsonNode node : List.of(column, query)) {
            for (String key : List.of("fieldType", "type", "componentType")) {
                if (node.path(key).isTextual() && StringUtils.hasText(node.path(key).asText())) {
                    return node.path(key).asText().trim().toUpperCase();
                }
            }
        }
        return "TEXT";
    }

    private JsonNode readConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private static String normalizeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            return "EQ";
        }
        return switch (operator.trim().toUpperCase()) {
            case "LIKE", "CONTAINS" -> "CONTAINS";
            case "GE", "GTE" -> "GTE";
            case "LE", "LTE" -> "LTE";
            case "GT", "LT", "IN", "BETWEEN" -> operator.trim().toUpperCase();
            default -> "EQ";
        };
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Map<String, Object> childMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
