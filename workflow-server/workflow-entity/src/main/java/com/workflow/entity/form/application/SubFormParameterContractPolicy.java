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

    /**
     * 初始化子级表单参数契约策略，保存构造参数供后续方法使用。
     */
    private SubFormParameterContractPolicy() {
    }

    /**
     * 整理输入参数结构数据，供调用方遍历或继续处理。
     *
     * @param form 表单，作为 {@code codec.readObject} 的输入影响后续处理
     * @param codec 编解码器，供本方法处理输入参数结构时使用
     * @return 输入参数结构键值结果，供调用方继续处理
     */
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

    /**
     * 处理契约，并将结果传给后续步骤。
     *
     * @param node 节点，供本方法处理契约时使用
     * @param codec 编解码器，供本方法处理契约时使用
     * @return 处理后的契约结果，供调用方继续处理
     */
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

    /**
     * 处理契约，并将结果传给后续步骤。
     *
     * @param props 属性，作为 {@code objectMap} 的输入影响后续处理
     * @return 处理后的契约结果，供调用方继续处理
     */
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

    /**
     * 处理关系配置，并将结果传给后续步骤。
     *
     * @param node 节点，供本方法处理关系配置时使用
     * @param codec 编解码器，供本方法处理关系配置时使用
     * @return 处理后的关系配置结果，供调用方继续处理
     */
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

    /**
     * 校验契约；不满足约束时阻止后续处理。
     *
     * @param contract 契约，作为 {@code validateShape} 的输入影响后续处理
     * @param inputSchema 输入结构，作为 {@code objectMap} 的输入影响后续处理
     * @param parentFields 父级字段，供本方法校验契约时使用
     * @param childFields 子级字段，供本方法校验契约时使用
     * @param childRefFieldCode 子级引用字段编码，后续用于校验契约时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验运行时目标集合；不满足约束时阻止后续处理。
     *
     * @param contract 契约，作为 {@code validateShape} 的输入影响后续处理
     * @param inputSchema 输入结构，作为 {@code objectMap} 的输入影响后续处理
     * @param childFields 子级字段，供本方法校验运行时目标集合时使用
     * @param childRefFieldCode 子级引用字段编码，后续用于校验运行时目标集合时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验{@code shape}；不满足约束时阻止后续处理。
     *
     * @param contract 契约，作为 {@code requireVersion} 的输入影响后续处理
     */
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

    /**
     * 解析参数集合；输出作为后续校验或处理的输入。
     *
     * @param contract 契约，作为 {@code resolveMapping} 的输入影响后续处理
     * @param inputSchema 输入结构，作为 {@code objectMap} 的输入影响后续处理
     * @param source 待解析参数集合的原始输入，结果供调用方继续使用
     * @return 参数集合键值结果，供调用方继续处理
     */
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

    /**
     * 应用空仅{@code initialization}，并将结果传给后续步骤。
     *
     * @param row 行，供本方法应用空仅{@code initialization}时使用
     * @param contract 契约，作为 {@code resolveMapping} 的输入影响后续处理
     * @param source 待应用空仅{@code initialization}的原始输入，结果供调用方继续使用
     * @param blockedFields {@code blocked}字段，作为 {@code blocked.addAll} 的输入影响后续处理
     * @return 空仅{@code initialization}条件成立时为 true，否则为 false
     */
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

    /**
     * 整理运行时来源数据，供调用方遍历或继续处理。
     *
     * @param parentRecordId 父级记录ID，后续用于处理运行时来源时定位或关联目标
     * @param parentData 父级数据，作为 {@code parent.put} 的输入影响后续处理
     * @param context 执行上下文，向后续运行时来源步骤传递身份、配置或状态
     * @param params 参数，作为 {@code source.put} 的输入影响后续处理
     * @param row 行，作为 {@code source.put} 的输入影响后续处理
     * @param relation 关系，作为 {@code source.put} 的输入影响后续处理
     * @return 运行时来源键值结果，供调用方继续处理
     */
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

    /**
     * 校验并获取版本；不满足约束时阻止后续处理。
     *
     * @param contract 契约，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    static void requireVersion(Contract contract) {
        if (contract != null
                && contract.present()
                && contract.version() != VERSION) {
            throw new IllegalArgumentException(
                    "不支持的子表单参数契约版本: "
                            + contract.version());
        }
    }

    /**
     * 解析映射；输出作为后续校验或处理的输入。
     *
     * @param mapping 映射，供本方法解析映射时使用
     * @param source 待解析映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
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

    /**
     * 校验映射{@code shape}；不满足约束时阻止后续处理。
     *
     * @param mapping 映射，供本方法校验映射{@code shape}时使用
     * @param label 标签，后续用于校验映射{@code shape}时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 解析{@code selector}；输出作为后续校验或处理的输入。
     *
     * @param source 待解析{@code selector}的原始输入，结果供调用方继续使用
     * @param selector {@code selector}，供本方法解析{@code selector}时使用
     * @return 解析后的{@code selector}结果，供调用方继续处理
     */
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

    /**
     * 校验{@code selector}；不满足约束时阻止后续处理。
     *
     * @param selector {@code selector}，供本方法校验{@code selector}时使用
     * @param parentByCode 父级编码，后续用于校验{@code selector}时定位或关联目标
     * @param label 标签，后续用于校验{@code selector}时匹配或展示
     * @return 校验后的{@code selector}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验并获取编码；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取编码的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验并获取编码时匹配或展示
     * @return 校验并获取后的编码文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验并获取兼容；不满足约束时阻止后续处理。
     *
     * @param source 待校验并获取兼容的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法校验并获取兼容时使用
     * @param label 标签，后续用于校验并获取兼容时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理结构类型，并将结果传给后续步骤。
     *
     * @param schema 结构，供本方法处理结构类型时使用
     * @return 处理后的结构类型结果，供调用方继续处理
     */
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

    /**
     * 处理字段类型，并将结果传给后续步骤。
     *
     * @param type 类型标识，决定后续字段类型采用的处理分支
     * @return 处理后的字段类型结果，供调用方继续处理
     */
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

    /**
     * 处理值类型，并将结果传给后续步骤。
     *
     * @param value 待处理值类型的原始输入，结果供调用方继续使用
     * @return 处理后的值类型结果，供调用方继续处理
     */
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

    /**
     * 整理字符串设置数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串设置的原始输入，结果供调用方继续使用
     * @return 子级表单参数契约策略集合，供调用方遍历或展示
     */
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

    /**
     * 判断是否空；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空的原始输入，结果供调用方继续使用
     * @return 空条件成立时为 true，否则为 false
     */
    private static boolean isEmpty(Object value) {
        return value == null
                || value instanceof String text && !StringUtils.hasText(text)
                || value instanceof Collection<?> collection
                        && collection.isEmpty();
    }

    /**
     * 整理对象映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理对象映射的原始输入，结果供调用方继续使用
     * @return 对象映射键值结果，供调用方继续处理
     */
    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    /**
     * 处理首个整数，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个整数结果，供调用方继续处理
     */
    private static Integer firstInteger(Object... values) {
        for (Object value : values) {
            Integer parsed = integer(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /**
     * 复制值；结果供后续流程传递或持久化。
     *
     * @param value 待复制值的原始输入，结果供调用方继续使用
     * @return 复制后的值结果，供调用方继续处理
     */
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

    /**
     * 封装契约的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param present 存在，保存在对象中供后续校验、查询或展示
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param parameterMapping 参数映射，保存在对象中供后续校验、查询或展示
     * @param fieldInitializationMapping 字段{@code initialization}映射，保存在对象中供后续校验、查询或展示
     */
    record Contract(
            boolean present,
            int version,
            Map<String, Object> parameterMapping,
            Map<String, Object> fieldInitializationMapping) {

        /**
         * 处理{@code absent}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code absent}结果，供调用方继续处理
         */
        static Contract absent() {
            return new Contract(false, 0, Map.of(), Map.of());
        }

        /**
         * 判断启用条件是否成立，供调用方选择后续分支。
         *
         * @return 启用条件成立时为 true，否则为 false
         */
        boolean enabled() {
            return present && version == VERSION;
        }
    }

    /**
     * 封装关系配置的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param fieldCode 字段编码，后续用于处理关系配置时定位或关联目标
     * @param childFormId 子级表单ID，后续用于处理关系配置时定位或关联目标
     * @param childFormReleaseId 子级表单发布版本ID，后续用于处理关系配置时定位或关联目标
     * @param childFormReleaseVersion 子级表单发布版本，保存在对象中供后续校验、查询或展示
     * @param relationCode 关系编码，后续用于处理关系配置时定位或关联目标
     * @param childEntityId 子级实体ID，后续用于处理关系配置时定位或关联目标
     * @param childRefFieldCode 子级引用字段编码，后续用于处理关系配置时定位或关联目标
     * @param relationType 关系类型标识，决定后续关系配置采用的处理分支
     */
    record RelationConfig(
            String fieldCode,
            String childFormId,
            String childFormReleaseId,
            Integer childFormReleaseVersion,
            String relationCode,
            String childEntityId,
            String childRefFieldCode,
            String relationType) {

        /**
         * 转换为映射；输出作为后续校验或处理的输入。
         *
         * @return 映射键值结果，供调用方继续处理
         */
        Map<String, Object> asMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            put(result, "fieldCode", fieldCode);
            put(result, "relationCode", relationCode);
            put(result, "childEntityId", childEntityId);
            put(result, "childRefFieldCode", childRefFieldCode);
            put(result, "relationType", relationType);
            return result;
        }

        /**
         * 写入关系配置；后续读取或执行将使用更新后的状态。
         *
         * @param target 目标，供本方法写入关系配置时使用
         * @param key 键，后续用于授权校验、关联或幂等去重
         * @param value 待写入关系配置的原始输入，结果供调用方继续使用
         */
        private static void put(
                Map<String, Object> target,
                String key,
                Object value) {
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    /**
     * 定义值类型的可选值；调用方据此选择对应的处理分支。
     */
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
