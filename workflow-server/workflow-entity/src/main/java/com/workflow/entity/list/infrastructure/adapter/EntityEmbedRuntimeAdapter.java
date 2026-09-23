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

    /**
     * 初始化实体嵌入式运行时适配器，保存构造参数供后续方法使用。
     *
     * @param runtimeService 运行时服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EntityEmbedRuntimeAdapter(
            EntityListRuntimeService runtimeService,
            ObjectMapper objectMapper) {
        this.runtimeService = runtimeService;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载列表结构；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listReleaseId 列表发布版本ID，后续用于加载列表结构时定位或关联目标
     * @param listReleaseVersion 列表发布版本，作为 {@code runtimeService.schemaPinned} 的输入影响后续处理
     * @return 符合条件的列表结构结果，供调用方继续处理
     */
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

    /**
     * 查询实体嵌入式运行时列表；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listReleaseId 列表发布版本ID，后续用于查询实体嵌入式运行时列表时定位或关联目标
     * @param listReleaseVersion 列表发布版本，作为 {@code loadListSchema} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param encodedClientFilters 已编码客户端过滤条件，供本方法查询实体嵌入式运行时列表时使用
     * @param trustedContextFilters 可信上下文过滤条件，供本方法查询实体嵌入式运行时列表时使用
     * @return 查询后的实体嵌入式运行时列表结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理行，并将结果传给后续步骤。
     *
     * @param source 待处理行的原始输入，结果供调用方继续使用
     * @param publishedFieldCodes 已发布字段编码集合，供本方法处理行时使用
     * @return 处理后的行结果，供调用方继续处理
     */
    private Row row(Object source, List<String> publishedFieldCodes) {
        Map<String, Object> object;
        Map<String, Object> data;
        Map<String, Object> extData;
        BeanWrapper bean = null;
        if (source instanceof EntityDataDTO dto) {
            // 审计字段使用公开的数据库列名，BeanWrapper 的 Java 属性名不能作为字段编码。
            object = new LinkedHashMap<>();
            object.put("id", dto.getId());
            object.put("create_time", dto.getCreateTime());
            object.put("update_time", dto.getUpdateTime());
            object.put("create_by", dto.getCreateBy());
            object.put("update_by", dto.getUpdateBy());
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
        if (source instanceof EntityDataDTO dto && dto.getUpdateTime() != null) {
            updatedAt = dto.getUpdateTime().toInstant(ZoneOffset.UTC);
        }
        return new Row(
                Objects.toString(object.get("id"), null),
                Collections.unmodifiableMap(values),
                updatedAt,
                actionCapabilities(source, object));
    }

    /**
     * 整理动作能力集合数据，供调用方遍历或继续处理。
     *
     * @param source 待处理动作能力集合的原始输入，结果供调用方继续使用
     * @param object 对象，供本方法处理动作能力集合时使用
     * @return 动作能力集合键值结果，供调用方继续处理
     */
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

    /**
     * 整理动作集合数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param placement {@code placement}，供本方法处理动作集合时使用
     * @return 动作集合，供调用方遍历或展示
     */
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

    /**
     * 整理选项数据，供调用方遍历或继续处理。
     *
     * @param field 字段，作为 {@code readConfig} 的输入影响后续处理
     * @return 选项集合，供调用方遍历或展示
     */
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

    /**
     * Field type must come from the immutable list release, never mutable entity metadata.
     *
     * @param field 字段，作为 {@code readConfig} 的输入影响后续处理
     * @return 处理后的已发布类型文本，供调用方比较或展示
     */
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

    /**
     * 读取配置；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 读取后的配置结果，供调用方继续处理
     */
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

    /**
     * 规范化操作人；输出作为后续校验或处理的输入。
     *
     * @param operator 操作人，供本方法规范化操作人时使用
     * @return 规范化后的操作人文本，供调用方比较或展示
     */
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

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 整理子级映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理子级映射的原始输入，结果供调用方继续使用
     * @return 子级映射键值结果，供调用方继续处理
     */
    private static Map<String, Object> childMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 整理不可变映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理不可变映射的原始输入，结果供调用方继续使用
     * @return 不可变映射键值结果，供调用方继续处理
     */
    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
