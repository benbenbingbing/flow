package com.workflow.entity.excel.config;

import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/** 关联转换的配置边界：目标来自实体定义，浏览器只可选择目标实体的可读基础属性。 */
@Component
@RequiredArgsConstructor
public class EntityExcelReferenceMetadata {
    private final EntityDefinitionMapper entities;
    private final EntityFieldMapper fields;
    private final EntityPublishedSnapshotService snapshots;
    private final SystemEntityFieldPolicy policy;
    private static final Set<EntityField.FieldType> SCALARS = Set.of(EntityField.FieldType.STRING,
            EntityField.FieldType.TEXT, EntityField.FieldType.INTEGER, EntityField.FieldType.LONG,
            EntityField.FieldType.DECIMAL, EntityField.FieldType.DATE, EntityField.FieldType.DATETIME,
            EntityField.FieldType.BOOLEAN, EntityField.FieldType.SELECT, EntityField.FieldType.RADIO);

    public static boolean isReference(EntityField field) {
        return field != null && (field.getFieldType() == EntityField.FieldType.REFERENCE
                || field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE);
    }

    /** 发布和执行都重新解析目标；已删除属性、未发布实体及敏感字段不得继续使用旧配置。 */
    public Target resolve(EntityField source) {
        if (!isReference(source)) throw new IllegalArgumentException("实体关联转换器只能绑定单选或多选实体字段");
        if (source.getFieldType() == EntityField.FieldType.MULTI_REFERENCE
                && (source.getRefEntityId() == null || source.getRefEntityId().isBlank()))
            throw new IllegalArgumentException("多选实体尚未配置关联目标，请先配置并发布：" + source.getFieldName());
        EntityDefinition entity = source.getRefEntityId() == null ? null : entities.selectById(source.getRefEntityId());
        if (entity == null && source.getRefEntityType() != null && source.getRefEntityType() != EntityField.RefEntityType.CUSTOM) {
            String code = switch (source.getRefEntityType()) {
                case USER -> "sys_user"; case DEPT -> "sys_organization"; case ROLE -> "sys_role"; case GROUP -> "sys_group";
                default -> "";
            };
            entity = entities.findByEntityCode(code).orElse(null);
        }
        if (entity == null) throw new IllegalArgumentException("关联实体不存在：" + source.getFieldName());
        boolean system = entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM;
        var available = system ? fields.findByEntityId(entity.getId()) : snapshots.getLatestByEntityId(entity.getId()).getFields();
        Map<String, Property> properties = new LinkedHashMap<>();
        for (EntityField field : available) {
            if (field.getFieldType() != null && SCALARS.contains(field.getFieldType())
                    && !Boolean.FALSE.equals(field.getRuntimeReadable()) && policy.isRuntimeReadable(entity, field)
                    && !policy.isSensitive(field.getFieldCode()) && field.getFieldCode().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                properties.put(field.getFieldCode(), new Property(field.getFieldCode(), field.getFieldName(),
                        field.getFieldType(), field.getFieldCode()));
            }
        }
        // 系统目录的名称/编码使用实际物理属性查询；保留统一 code/name 供方案跨界面回显。
        if (system) {
            alias(properties, "name", "名称", policy.displayField(entity.getEntityCode()));
            String codeField = switch (entity.getEntityCode()) {
                case "sys_user" -> "username"; case "sys_role" -> "role_code"; case "sys_organization" -> "org_code";
                case "sys_group" -> "group_code"; case "sys_dict" -> "dict_code"; case "sys_dict_item" -> "item_code";
                default -> "id";
            };
            alias(properties, "code", "编码", codeField);
        }
        return new Target(entity.getEntityCode(), entity.getEntityName(), system,
                source.getFieldType() == EntityField.FieldType.MULTI_REFERENCE, Collections.unmodifiableMap(properties));
    }

    private static void alias(Map<String, Property> properties, String code, String label, String actual) {
        Property property = properties.get(actual);
        if (property != null) properties.put(code, new Property(code, label, property.fieldType(), property.queryField()));
    }

    /** 返回经白名单验证的规则；显式清空不是默认值，必须报错，避免用户配置被悄悄替换。 */
    public static Settings settings(Target target, Map<String, Object> options, boolean importing, boolean exporting) {
        List<String> match = List.of("code", "name");
        if (options.containsKey("matchFields")) {
            if (!(options.get("matchFields") instanceof List<?> values) || values.isEmpty() || values.size() > 10
                    || values.stream().anyMatch(value -> !(value instanceof String)))
                throw new IllegalArgumentException("导入匹配属性须选择1至10项");
            match = values.stream().map(String.class::cast).distinct().toList();
        }
        String output = Objects.toString(options.getOrDefault("exportField", "name"), "");
        if (importing) match.forEach(field -> requireProperty(target, field));
        if (exporting) requireProperty(target, output);
        String separator = Objects.toString(options.getOrDefault("separator", ";"), "");
        if (!Set.of(";", "\n").contains(separator)) throw new IllegalArgumentException("多值分隔符只支持英文分号或换行");
        return new Settings(match, output, separator);
    }

    private static void requireProperty(Target target, String field) {
        if (!target.properties().containsKey(field)) throw new IllegalArgumentException("关联实体属性不存在或不可读取：" + field);
    }

    public record Property(String fieldCode, String fieldName, EntityField.FieldType fieldType, String queryField) { }
    public record Target(String entityCode, String entityName, boolean system, boolean multiple, Map<String, Property> properties) { }
    /** matchFields 是 OR 精确匹配，所有属性命中的记录去重后必须唯一；不是按属性优先级取第一条。 */
    public record Settings(List<String> matchFields, String exportField, String separator) { }
}
