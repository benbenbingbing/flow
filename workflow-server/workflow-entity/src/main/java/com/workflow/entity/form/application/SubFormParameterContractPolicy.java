package com.workflow.entity.form.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 子表单输入参数契约的纯配置策略。
 *
 * <p>负责解析发布快照中的参数 Schema、父节点参数映射，校验稳定来源路径，
 * 并在运行时执行可信参数解析与 EMPTY_ONLY 子字段初始化。</p>
 */
final class SubFormParameterContractPolicy {

    static final int VERSION = 1;

    private static final Pattern CODE =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Set<String> SYSTEM_MANAGED_FIELDS = Set.of(
            "id", "create_time", "update_time", "create_by",
            "update_by", "deleted");

    private SubFormParameterContractPolicy() {
    }

    static Map<String, Object> inputParameterSchema(
            EntityForm form,
            JsonDocumentCodec codec) {
        if (form == null || !StringUtils.hasText(form.getViewConfig())) {
            return Map.of();
        }
        Map<String, Object> viewConfig = codec.readObject(
                form.getViewConfig(),
                "子表单视图配置");
        return objectMap(viewConfig.get("inputParameterSchema"));
    }

    static Contract contract(
            EntityFormNode node,
            JsonDocumentCodec codec) {
        if (node == null || !StringUtils.hasText(node.getPropsDocument())) {
            return Contract.absent();
        }
        return contract(codec.readObject(
                node.getPropsDocument(),
                "子表单节点属性"));
    }

    static Contract contract(Map<String, Object> props) {
        Map<String, Object> componentProps =
                objectMap(props == null
                        ? null : props.get("componentProps"));
        Map<String, Object> subFormConfig =
                objectMap(componentProps.get("subFormConfig"));
        Object configured = subFormConfig.get("parameterContract");
        if (!(configured instanceof Map<?, ?>)) {
            return Contract.absent();
        }
        Map<String, Object> value = objectMap(configured);
        Integer version = integer(value.get("version"));
        return new Contract(
                true,
                version == null ? 0 : version,
                objectMap(value.get("parameterMapping")),
                objectMap(value.get("fieldInitializationMapping")));
    }

    static RelationConfig relationConfig(
            EntityFormNode node,
            JsonDocumentCodec codec) {
        Map<String, Object> props =
                node == null || !StringUtils.hasText(node.getPropsDocument())
                        ? Map.of()
                        : codec.readObject(
                                node.getPropsDocument(),
                                "子表单节点属性");
        Map<String, Object> componentProps =
                objectMap(props.get("componentProps"));
        Map<String, Object> nested =
                objectMap(componentProps.get("subFormConfig"));
        Map<String, Object> direct =
                objectMap(props.get("subFormConfig"));
        return new RelationConfig(
                firstText(
                        props.get("fieldCode"),
                        node == null ? null : node.getNodeKey()),
                firstText(
                        props.get("childFormId"),
                        props.get("refFormId"),
                        props.get("publishedFormId"),
                        direct.get("childFormId"),
                        direct.get("refFormId"),
                        direct.get("publishedFormId"),
                        nested.get("childFormId"),
                        nested.get("refFormId"),
                        nested.get("publishedFormId")),
                firstText(
                        props.get("childFormReleaseId"),
                        props.get("refFormReleaseId"),
                        props.get("publishedFormReleaseId"),
                        direct.get("childFormReleaseId"),
                        direct.get("refFormReleaseId"),
                        direct.get("publishedFormReleaseId"),
                        nested.get("childFormReleaseId"),
                        nested.get("refFormReleaseId"),
                        nested.get("publishedFormReleaseId")),
                firstInteger(
                        props.get("childFormReleaseVersion"),
                        props.get("refFormReleaseVersion"),
                        props.get("publishedFormReleaseVersion"),
                        direct.get("childFormReleaseVersion"),
                        direct.get("refFormReleaseVersion"),
                        direct.get("publishedFormReleaseVersion"),
                        nested.get("childFormReleaseVersion"),
                        nested.get("refFormReleaseVersion"),
                        nested.get("publishedFormReleaseVersion")),
                firstText(
                        nested.get("relationCode"),
                        direct.get("relationCode")),
                firstText(
                        nested.get("childEntityId"),
                        nested.get("refEntityId"),
                        direct.get("childEntityId"),
                        direct.get("refEntityId")),
                firstText(
                        nested.get("childRefFieldCode"),
                        nested.get("refFieldCode"),
                        direct.get("childRefFieldCode"),
                        direct.get("refFieldCode")),
                firstText(
                        nested.get("relationType"),
                        direct.get("relationType")));
    }

    static void validateContract(
            Contract contract,
            Map<String, Object> inputSchema,
            List<EntityField> parentFields,
            List<EntityFormField> childFields,
            String childRefFieldCode) {
        if (contract == null || !contract.present()) {
            return;
        }
        validateShape(contract);
        Map<String, Object> properties =
                objectMap(inputSchema == null
                        ? null : inputSchema.get("properties"));
        Set<String> required = stringSet(
                inputSchema == null
                        ? null : inputSchema.get("required"));
        Map<String, EntityField> parentByCode = new LinkedHashMap<>();
        for (EntityField field : parentFields == null
                ? List.<EntityField>of() : parentFields) {
            if (field != null && StringUtils.hasText(field.getFieldCode())) {
                parentByCode.put(field.getFieldCode(), field);
            }
        }
        Map<String, EntityFormField> childByCode = new LinkedHashMap<>();
        for (EntityFormField field : childFields == null
                ? List.<EntityFormField>of() : childFields) {
            if (field != null && StringUtils.hasText(field.getFieldCode())) {
                childByCode.put(field.getFieldCode(), field);
            }
        }

        for (Map.Entry<String, Object> entry
                : contract.parameterMapping().entrySet()) {
            String target = requireCode(entry.getKey(), "子表单参数编码");
            Map<String, Object> targetSchema =
                    objectMap(properties.get(target));
            if (targetSchema.isEmpty()) {
                throw new IllegalArgumentException(
                        "子表单参数映射目标不存在或已失效: " + target);
            }
            ValueType sourceType = validateSelector(
                    entry.getValue(),
                    parentByCode,
                    "子表单参数 " + target);
            ValueType targetType = schemaType(targetSchema);
            requireCompatible(
                    sourceType,
                    targetType,
                    "子表单参数 " + target);
        }
        for (String requiredCode : required) {
            Map<String, Object> property =
                    objectMap(properties.get(requiredCode));
            if (!contract.parameterMapping().containsKey(requiredCode)
                    && !property.containsKey("default")) {
                throw new IllegalArgumentException(
                        "子表单必填参数未配置来源: " + requiredCode);
            }
        }

        Set<String> blockedFields =
                new LinkedHashSet<>(SYSTEM_MANAGED_FIELDS);
        if (StringUtils.hasText(childRefFieldCode)) {
            blockedFields.add(childRefFieldCode);
        }
        for (Map.Entry<String, Object> entry
                : contract.fieldInitializationMapping().entrySet()) {
            String target = requireCode(entry.getKey(), "子实体字段编码");
            EntityFormField childField = childByCode.get(target);
            if (childField == null) {
                throw new IllegalArgumentException(
                        "子字段初始化目标不存在或已失效: " + target);
            }
            if (blockedFields.contains(target)
                    || Objects.equals(childField.getIsReadonly(), 1)) {
                throw new IllegalArgumentException(
                        "子字段由系统维护或不可写，不能配置初始化: " + target);
            }
            ValueType sourceType = validateSelector(
                    entry.getValue(),
                    parentByCode,
                    "子字段 " + target);
            requireCompatible(
                    sourceType,
                    fieldType(childField.getFieldType()),
                    "子字段 " + target);
        }
    }

    static void validateRuntimeTargets(
            Contract contract,
            Map<String, Object> inputSchema,
            List<EntityFormField> childFields,
            String childRefFieldCode) {
        if (contract == null || !contract.present()) {
            return;
        }
        validateShape(contract);
        Map<String, Object> properties =
                objectMap(inputSchema == null
                        ? null : inputSchema.get("properties"));
        for (String target : contract.parameterMapping().keySet()) {
            if (!properties.containsKey(target)) {
                throw new IllegalArgumentException(
                        "子表单参数映射目标不存在或已失效: "
                                + target);
            }
        }
        for (String required : stringSet(
                inputSchema == null
                        ? null : inputSchema.get("required"))) {
            Map<String, Object> property =
                    objectMap(properties.get(required));
            if (!contract.parameterMapping().containsKey(required)
                    && !property.containsKey("default")) {
                throw new IllegalArgumentException(
                        "子表单必填参数未配置来源: "
                                + required);
            }
        }
        Map<String, EntityFormField> childByCode =
                new LinkedHashMap<>();
        for (EntityFormField field : childFields == null
                ? List.<EntityFormField>of() : childFields) {
            if (field != null && StringUtils.hasText(
                    field.getFieldCode())) {
                childByCode.put(field.getFieldCode(), field);
            }
        }
        Set<String> blockedFields =
                new LinkedHashSet<>(SYSTEM_MANAGED_FIELDS);
        if (StringUtils.hasText(childRefFieldCode)) {
            blockedFields.add(childRefFieldCode);
        }
        for (String target
                : contract.fieldInitializationMapping().keySet()) {
            EntityFormField childField = childByCode.get(target);
            if (childField == null) {
                throw new IllegalArgumentException(
                        "子字段初始化目标不存在或已失效: "
                                + target);
            }
            if (blockedFields.contains(target)
                    || Objects.equals(childField.getIsReadonly(), 1)) {
                throw new IllegalArgumentException(
                        "子字段由系统维护或不可写，不能配置初始化: "
                                + target);
            }
        }
    }

    static void validateShape(Contract contract) {
        if (contract == null || !contract.present()) {
            return;
        }
        requireVersion(contract);
        validateMappingShape(
                contract.parameterMapping(),
                "子表单运行参数");
        validateMappingShape(
                contract.fieldInitializationMapping(),
                "子字段初始化");
    }

    static Map<String, Object> resolveParameters(
            Contract contract,
            Map<String, Object> inputSchema,
            Map<String, Object> source) {
        if (contract == null || !contract.enabled()) {
            return Map.of();
        }
        Map<String, Object> result = resolveMapping(
                contract.parameterMapping(),
                source);
        Map<String, Object> properties = objectMap(
                inputSchema == null
                        ? null : inputSchema.get("properties"));
        properties.forEach((code, definitionValue) -> {
            Map<String, Object> definition = objectMap(definitionValue);
            if (!result.containsKey(code)
                    && definition.containsKey("default")) {
                result.put(code, copyValue(definition.get("default")));
            }
        });
        return result;
    }

    static boolean applyEmptyOnlyInitialization(
            Map<String, Object> row,
            Contract contract,
            Map<String, Object> source,
            Collection<String> blockedFields) {
        if (row == null || contract == null || !contract.enabled()) {
            return false;
        }
        Set<String> blocked =
                new LinkedHashSet<>(SYSTEM_MANAGED_FIELDS);
        if (blockedFields != null) {
            blocked.addAll(blockedFields);
        }
        boolean changed = false;
        Map<String, Object> values = resolveMapping(
                contract.fieldInitializationMapping(),
                source);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String fieldCode = entry.getKey();
            if (blocked.contains(fieldCode)
                    || !isEmpty(row.get(fieldCode))
                    || entry.getValue() == null) {
                continue;
            }
            row.put(fieldCode, copyValue(entry.getValue()));
            changed = true;
        }
        return changed;
    }

    static Map<String, Object> runtimeSource(
            String parentRecordId,
            Map<String, Object> parentData,
            Map<String, Object> context,
            Map<String, Object> params,
            Map<String, Object> row,
            Map<String, Object> relation) {
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("recordId", parentRecordId);
        parent.put(
                "data",
                parentData == null ? Map.of() : parentData);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("parent", parent);
        source.put("context", context == null ? Map.of() : context);
        source.put("params", params == null ? Map.of() : params);
        source.put("row", row == null ? Map.of() : row);
        source.put("relation", relation == null ? Map.of() : relation);
        return source;
    }

    static void requireVersion(Contract contract) {
        if (contract != null
                && contract.present()
                && contract.version() != VERSION) {
            throw new IllegalArgumentException(
                    "不支持的子表单参数契约版本: "
                            + contract.version());
        }
    }

    private static Map<String, Object> resolveMapping(
            Map<String, Object> mapping,
            Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (mapping == null) {
            return result;
        }
        mapping.forEach((target, selector) -> {
            Object value = resolveSelector(source, selector);
            if (value != null) {
                result.put(target, value);
            }
        });
        return result;
    }

    private static void validateMappingShape(
            Map<String, Object> mapping,
            String label) {
        for (Map.Entry<String, Object> entry
                : mapping.entrySet()) {
            requireCode(entry.getKey(), label + "目标编码");
            Object selector = entry.getValue();
            if (selector instanceof Map<?, ?> literal) {
                if (literal.size() != 1
                        || !literal.containsKey("literal")) {
                    throw new IllegalArgumentException(
                            label + "固定值配置不合法: "
                                    + entry.getKey());
                }
                continue;
            }
            if (!(selector instanceof String path)
                    || !StringUtils.hasText(path)) {
                throw new IllegalArgumentException(
                        label + "来源不能为空: "
                                + entry.getKey());
            }
            String source = path.trim();
            if (!"parent.recordId".equals(source)
                    && !source.startsWith("parent.data.")
                    && !source.startsWith("context.")) {
                throw new IllegalArgumentException(
                        label + "仅支持 parent.recordId、"
                                + "parent.data.<字段>、context.<键> 或固定值: "
                                + entry.getKey());
            }
        }
    }

    private static Object resolveSelector(
            Map<String, Object> source,
            Object selector) {
        if (selector instanceof Map<?, ?> literal
                && literal.containsKey("literal")) {
            return copyValue(literal.get("literal"));
        }
        Object current = source;
        for (String part : String.valueOf(selector)
                .split("\\.")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return copyValue(current);
    }

    private static ValueType validateSelector(
            Object selector,
            Map<String, EntityField> parentByCode,
            String label) {
        if (selector instanceof Map<?, ?> literal) {
            if (literal.size() != 1
                    || !literal.containsKey("literal")) {
                throw new IllegalArgumentException(
                        label + " 固定值配置不合法");
            }
            return valueType(literal.get("literal"));
        }
        if (!(selector instanceof String path)
                || !StringUtils.hasText(path)) {
            throw new IllegalArgumentException(
                    label + " 的参数来源不能为空");
        }
        String value = path.trim();
        if ("parent.recordId".equals(value)) {
            return ValueType.STRING;
        }
        if (value.startsWith("parent.data.")) {
            String fieldCode =
                    value.substring("parent.data.".length());
            EntityField field = parentByCode.get(fieldCode);
            if (field == null) {
                throw new IllegalArgumentException(
                        label + " 引用的父字段不存在: "
                                + fieldCode);
            }
            return fieldType(field.getFieldType() == null
                    ? null : field.getFieldType().name());
        }
        if (value.startsWith("context.")
                && StringUtils.hasText(
                        value.substring("context.".length()))) {
            return ValueType.UNKNOWN;
        }
        throw new IllegalArgumentException(
                label + " 仅支持 parent.recordId、parent.data.<字段>、"
                        + "context.<键> 或固定值");
    }

    private static String requireCode(
            String value,
            String label) {
        String normalized = value == null ? "" : value.trim();
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    label + "不合法: " + value);
        }
        return normalized;
    }

    private static void requireCompatible(
            ValueType source,
            ValueType target,
            String label) {
        if (source == ValueType.UNKNOWN
                || target == ValueType.UNKNOWN
                || source == target
                || target == ValueType.STRING
                        && source == ValueType.TEMPORAL
                || target == ValueType.NUMBER
                        && source == ValueType.INTEGER) {
            return;
        }
        throw new IllegalArgumentException(
                label + " 的来源类型与目标类型不兼容");
    }

    private static ValueType schemaType(
            Map<String, Object> schema) {
        return switch (String.valueOf(
                        schema.getOrDefault("type", ""))
                .trim()
                .toLowerCase(Locale.ROOT)) {
            case "string" -> ValueType.STRING;
            case "number" -> ValueType.NUMBER;
            case "integer" -> ValueType.INTEGER;
            case "boolean" -> ValueType.BOOLEAN;
            case "array" -> ValueType.ARRAY;
            case "object" -> ValueType.OBJECT;
            default -> ValueType.UNKNOWN;
        };
    }

    private static ValueType fieldType(String type) {
        return switch (String.valueOf(type)
                .trim()
                .toUpperCase(Locale.ROOT)) {
            case "INTEGER", "LONG" -> ValueType.INTEGER;
            case "DECIMAL", "DOUBLE" -> ValueType.NUMBER;
            case "BOOLEAN" -> ValueType.BOOLEAN;
            case "MULTI_SELECT", "CHECKBOX",
                    "MULTI_REFERENCE" -> ValueType.ARRAY;
            case "SUB_FORM" -> ValueType.OBJECT;
            case "DATE", "DATETIME" -> ValueType.TEMPORAL;
            case "STRING", "TEXT", "RICH_TEXT", "SELECT", "RADIO",
                    "REFERENCE", "USER", "DEPT", "ROLE", "GROUP",
                    "FILE", "IMAGE" -> ValueType.STRING;
            default -> ValueType.UNKNOWN;
        };
    }

    private static ValueType valueType(Object value) {
        if (value == null) {
            return ValueType.UNKNOWN;
        }
        if (value instanceof Boolean) {
            return ValueType.BOOLEAN;
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return ValueType.INTEGER;
        }
        if (value instanceof Number) {
            return ValueType.NUMBER;
        }
        if (value instanceof Map<?, ?>) {
            return ValueType.OBJECT;
        }
        if (value instanceof List<?>) {
            return ValueType.ARRAY;
        }
        return ValueType.STRING;
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        collection.forEach(item -> {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item).trim());
            }
        });
        return result;
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || value instanceof String text && !StringUtils.hasText(text)
                || value instanceof Collection<?> collection
                        && collection.isEmpty();
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "子表单参数契约版本格式不正确: " + value);
        }
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static Integer firstInteger(Object... values) {
        for (Object value : values) {
            Integer parsed = integer(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) ->
                    copy.put(String.valueOf(key), copyValue(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyValue(item)));
            return copy;
        }
        return value;
    }

    record Contract(
            boolean present,
            int version,
            Map<String, Object> parameterMapping,
            Map<String, Object> fieldInitializationMapping) {

        static Contract absent() {
            return new Contract(false, 0, Map.of(), Map.of());
        }

        boolean enabled() {
            return present && version == VERSION;
        }
    }

    record RelationConfig(
            String fieldCode,
            String childFormId,
            String childFormReleaseId,
            Integer childFormReleaseVersion,
            String relationCode,
            String childEntityId,
            String childRefFieldCode,
            String relationType) {

        Map<String, Object> asMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            put(result, "fieldCode", fieldCode);
            put(result, "relationCode", relationCode);
            put(result, "childEntityId", childEntityId);
            put(result, "childRefFieldCode", childRefFieldCode);
            put(result, "relationType", relationType);
            return result;
        }

        private static void put(
                Map<String, Object> target,
                String key,
                Object value) {
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private enum ValueType {
        STRING,
        NUMBER,
        INTEGER,
        BOOLEAN,
        ARRAY,
        OBJECT,
        TEMPORAL,
        UNKNOWN
    }
}
