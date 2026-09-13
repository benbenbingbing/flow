package com.workflow.entity.form.application.validation;

import com.workflow.entity.ui.application.validation.StructuredConfigValidator;

import com.workflow.entity.form.application.EntityFormActionConfigPolicy;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.application.FormUniqueRulePolicy;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.ui.application.UiDataSourceDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实体表单配置校验器
 * 
 * 对表单及表单字段配置进行保存前的合法性校验：表单标识/名称、自定义组件版本锁定、
 * 字段编码唯一性与格式、字段校验规则（范围、长度、格式、正则）、
 * 字段在不同运行模式（create/edit/approve/view）下的可见与可编辑权限、栅格宽度等。
 */
@Component
@RequiredArgsConstructor
public class EntityFormConfigurationValidator {

    /** 表单标识最大长度，与 entity_form.form_key 字段保持一致 */
    public static final int FORM_KEY_MAX_LENGTH = 100;
    /** 表单标识正则：字母开头，字母数字下划线短横线，长度 1~100 */
    private static final Pattern FORM_KEY = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_-]{0,"
                    + (FORM_KEY_MAX_LENGTH - 1)
                    + "}");
    /** 字段编码正则：字母开头，字母数字下划线，长度 1~100 */
    private static final Pattern FIELD_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    /** 扩展组件标识正则：字母开头，字母数字下划线点短横线，长度 1~100 */
    private static final Pattern EXTENSION_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");
    /** 支持的表单运行模式 */
    private static final Set<String> MODES = Set.of("create", "edit", "approve", "view");
    /** 支持的字段格式校验类型 */
    private static final Set<String> FORMATS = Set.of("", "EMAIL", "PHONE", "URL");
    /** 附件项稳定标识格式 */
    private static final Pattern ATTACHMENT_ITEM_KEY =
            Pattern.compile("afi_[A-Za-z0-9_-]{1,60}");
    /** 三类字段状态条件共享同一份结构化条件契约。 */
    private static final List<Map.Entry<String, String>>
            LINKAGE_CONDITION_LABELS = List.of(
                    Map.entry("visibilityConditionConfig", "显示条件"),
                    Map.entry("disabledConditionConfig", "禁用条件"),
                    Map.entry("requiredConditionConfig", "必填条件"));

    private final StructuredConfigValidator structuredConfigValidator;
    private final EntityFormActionConfigPolicy formActionConfigPolicy;
    private final UiDataSourceDefinitionValidator dataSourceDefinitionValidator;
    private final PublishedFormConditionEvaluator conditionEvaluator;
    private final EntityFieldMapper entityFieldMapper;
    private final EntityFieldFileItemMapper fileItemMapper;
    private final FormUniqueRulePolicy uniqueRulePolicy;

    /**
     * 校验表单整体配置。
     *
     * @param form 表单对象（含字段列表）
     * @throws IllegalArgumentException 实体为空、名称为空、标识不合法、组件版本未锁定等校验失败时抛出
     */
    public void validateForm(EntityForm form) {
        validateFormIdentity(form);
        validateExtensionName(form.getCustomComponent(), "自定义表单组件");
        if (StringUtils.hasText(form.getCustomComponent())
                && (form.getCustomComponentVersion() == null
                || form.getCustomComponentVersion() < 1
                || form.getCustomComponentSnapshotVersion() == null
                || form.getCustomComponentSnapshotVersion() < 1)) {
            throw new IllegalArgumentException(
                    "自定义表单组件必须锁定实现版本和配置快照版本");
        }
        if (!StringUtils.hasText(form.getCustomComponent())
                && (form.getCustomComponentVersion() != null
                || form.getCustomComponentSnapshotVersion() != null)) {
            throw new IllegalArgumentException(
                    "未配置自定义表单组件时不能单独保存组件版本");
        }
        Map<String, Object> viewConfig =
                structuredConfigValidator.parseObject(
                        form.getViewConfig(),
                        "表单视图配置");
        viewConfig = formActionConfigPolicy.normalizeAndValidate(
                viewConfig,
                false,
                Set.of(),
                false,
                Set.of(),
                false);
        validateInputParameterSchema(viewConfig);
        form.setViewConfig(StringUtils.hasText(form.getViewConfig())
                ? structuredConfigValidator.writeJson(
                        viewConfig, "表单视图配置")
                : null);
        Map<String, Object> dataSourceBindings =
                structuredConfigValidator.parseObject(
                        form.getDataSourceBindingsDocument(),
                        "表单级数据源绑定");
        validateUniqueDataSourceOutputTargets(dataSourceBindings);
        form.setDataSourceBindingsDocument(
                blankToNull(
                        form.getDataSourceBindingsDocument()));
        validateFields(form.getFields());
        List<EntityField> entityFields = entityFieldMapper.findByEntityId(
                form.getEntityId());
        validateConditionalRules(
                form.getFields(),
                validEntityProperties(form, entityFields),
                entityFields == null ? List.of() : entityFields);
        uniqueRulePolicy.validatePublished(
                form.getFields(),
                persistentEntityProperties(entityFields));
    }

    /**
     * 校验同一生命周期位置内的数据源步骤不会写入相同目标路径。
     *
     * <p>运行时会按步骤顺序合并输出，重复目标会被后一步静默覆盖；服务端必须在保存与发布共用的
     * 表单校验入口阻断该配置，避免绕过前端校验后产生顺序相关的数据丢失。
     */
    private void validateUniqueDataSourceOutputTargets(
            Map<String, Object> dataSourceBindings) {
        for (Map.Entry<String, Object> usageEntry
                : dataSourceBindings.entrySet()) {
            Object configured = usageEntry.getValue();
            if (configured == null) {
                continue;
            }
            List<?> bindings = configured instanceof List<?> list
                    ? list : List.of(configured);
            Map<String, Integer> firstStepByTarget =
                    new LinkedHashMap<>();
            for (int index = 0; index < bindings.size(); index++) {
                Object bindingValue = bindings.get(index);
                if (!(bindingValue instanceof Map<?, ?> binding)
                        || !(binding.get("outputMapping")
                        instanceof Map<?, ?> outputMapping)) {
                    continue;
                }
                for (Object targetValue : outputMapping.keySet()) {
                    String targetPath = normalizeDataSourceOutputTarget(
                            targetValue);
                    if (targetPath.isEmpty()) {
                        continue;
                    }
                    Integer previousStep = firstStepByTarget.putIfAbsent(
                            targetPath,
                            index + 1);
                    if (previousStep != null) {
                        throw new IllegalArgumentException(
                                "表单级数据源 "
                                        + usageEntry.getKey()
                                        + " 的步骤 "
                                        + previousStep
                                        + " 与步骤 "
                                        + (index + 1)
                                        + " 重复写入目标路径“"
                                        + targetPath
                                        + "”");
                    }
                }
            }
        }
    }

    /**
     * 按前端运行时 {@code split(".").filter(Boolean)} 的路径语义归一化目标。
     *
     * <p>例如 {@code owner..name}、{@code owner. .name} 与 {@code owner.name} 实际都会写到
     * 同一字段；保存时必须将其视为同一路径，避免通过空路径段绕过重复写入校验。
     */
    private String normalizeDataSourceOutputTarget(Object targetValue) {
        if (targetValue == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : String.valueOf(targetValue)
                .split("\\.", -1)) {
            if (segment.isBlank()) {
                continue;
            }
            if (!normalized.isEmpty()) {
                normalized.append('.');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    /**
     * 校验表单名称和稳定标识，供创建、更新与复制入口复用。
     */
    public void validateFormIdentity(EntityForm form) {
        if (form == null || !StringUtils.hasText(form.getEntityId())) {
            throw new IllegalArgumentException("表单实体不能为空");
        }
        if (!StringUtils.hasText(form.getFormName())) {
            throw new IllegalArgumentException("表单名称不能为空");
        }
        if (!StringUtils.hasText(form.getFormKey()) || !FORM_KEY.matcher(form.getFormKey()).matches()) {
            throw new IllegalArgumentException("表单标识只能包含字母、数字、下划线和短横线，且必须以字母开头");
        }
    }

    /** 校验表单作为子表单使用时声明的输入参数 Schema。 */
    private void validateInputParameterSchema(
            Map<String, Object> viewConfig) {
        Object configured = viewConfig.get("inputParameterSchema");
        if (configured == null) {
            return;
        }
        if (!(configured instanceof Map<?, ?> rawSchema)) {
            throw new IllegalArgumentException(
                    "子表单输入参数契约必须为 Schema 对象");
        }
        Map<String, Object> schema = stringMap(rawSchema);
        if (schema.isEmpty()) {
            return;
        }
        Object type = schema.get("type");
        if (type != null
                && !"object".equalsIgnoreCase(
                        String.valueOf(type).trim())) {
            throw new IllegalArgumentException(
                    "子表单输入参数契约根类型必须为 object");
        }
        Object propertiesValue = schema.get("properties");
        if (propertiesValue != null
                && !(propertiesValue instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "子表单输入参数 properties 必须为对象");
        }
        Map<String, Object> properties =
                propertiesValue instanceof Map<?, ?> propertiesMap
                        ? stringMap(propertiesMap)
                        : Map.of();
        for (Map.Entry<String, Object> entry
                : properties.entrySet()) {
            if (!FIELD_CODE.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException(
                        "子表单输入参数编码不合法: "
                                + entry.getKey());
            }
            if (!(entry.getValue()
                    instanceof Map<?, ?> definition)) {
                throw new IllegalArgumentException(
                        "子表单输入参数定义必须为对象: "
                                + entry.getKey());
            }
            Object title = definition.get("title");
            if (!(title instanceof String text)
                    || !StringUtils.hasText(text)) {
                throw new IllegalArgumentException(
                        "子表单输入参数中文名称不能为空: "
                                + entry.getKey());
            }
        }
        dataSourceDefinitionValidator.validateSchemaDefinition(
                schema,
                "子表单输入参数契约");

        Map<String, Object> defaults = new LinkedHashMap<>();
        properties.forEach((code, definitionValue) -> {
            if (definitionValue instanceof Map<?, ?> definition
                    && definition.containsKey("default")) {
                defaults.put(code, definition.get("default"));
            }
        });
        if (!defaults.isEmpty()) {
            Map<String, Object> defaultValidationSchema =
                    new LinkedHashMap<>(schema);
            defaultValidationSchema.remove("required");
            dataSourceDefinitionValidator.validateSchemaValue(
                    defaultValidationSchema,
                    defaults,
                    "子表单输入参数默认值");
        }
    }

    /**
     * 校验表单字段列表。
     *
     * @param fields 字段列表，为 null 时跳过
     * @throws IllegalArgumentException 字段数量超过 300 或字段配置不合法时抛出
     */
    public void validateFields(List<EntityFormField> fields) {
        if (fields == null) {
            return;
        }
        if (fields.size() > 300) {
            throw new IllegalArgumentException("单个表单最多配置 300 个项目");
        }
        Set<String> fieldCodes = new HashSet<>();
        for (EntityFormField field : fields) {
            validateField(field, fieldCodes);
        }
        // 字段整包保存时至少校验规则结构；发布时 validateForm 会再校验条件字段引用。
        uniqueRulePolicy.validate(fields, Set.of());
        validateConditionalRules(fields, Set.of(), null);
    }

    /** 校验单个字段：编码格式、唯一性、组件标识、各类配置 JSON 合法性及栅格宽度 */
    private void validateField(EntityFormField field, Set<String> fieldCodes) {
        if (field == null || !StringUtils.hasText(field.getFieldCode())
                || !FIELD_CODE.matcher(field.getFieldCode()).matches()) {
            throw new IllegalArgumentException("表单字段编码不合法");
        }
        if (!fieldCodes.add(field.getFieldCode().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("表单字段编码重复: " + field.getFieldCode());
        }
        validateExtensionName(field.getComponentType(), "表单字段组件");
        Map<String, Object> validation = structuredConfigValidator.parseObject(
                field.getValidationRules(),
                "字段校验规则");
        Map<String, Object> extension = structuredConfigValidator.parseObject(
                field.getExtensionConfig(),
                "字段扩展配置");
        structuredConfigValidator.parseObject(field.getComponentProps(), "字段组件配置");
        field.setValidationRules(blankToNull(field.getValidationRules()));
        field.setExtensionConfig(blankToNull(field.getExtensionConfig()));
        validateValidationRules(validation);
        validateModeAccess(extension);
        if (field.getGridSpan() != null && (field.getGridSpan() < 1 || field.getGridSpan() > 24)) {
            throw new IllegalArgumentException("字段栅格宽度必须在 1 到 24 之间");
        }
    }

    /**
     * 统一校验显隐、禁用、必填条件及附件项逻辑必填。
     *
     * <p>浏览器会在结构化条件不完整时回退历史表达式，但新保存/发布的快照必须固定一份
     * 完整结构化契约，避免服务端鉴权、提交校验与页面联动依赖不同分支。</p>
     */
    private void validateConditionalRules(
            List<EntityFormField> fields,
            Set<String> validProperties,
            List<EntityField> currentEntityFields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        for (EntityFormField field : fields) {
            Map<String, Object> componentProps =
                    structuredConfigValidator.parseObject(
                            field.getComponentProps(),
                            "字段组件配置");
            Map<String, Object> linkageRules = mapValue(
                    componentProps.get("linkageRules"));
            for (Map.Entry<String, String> definition
                    : LINKAGE_CONDITION_LABELS) {
                Object condition = linkageRules.get(definition.getKey());
                if (condition != null) {
                    conditionEvaluator.validateStructured(
                            condition,
                            validProperties,
                            fieldLabel(field)
                                    + definition.getValue()
                                    + "：");
                }
            }

            Object configured = componentProps.containsKey(
                    "attachmentItemRequiredRules")
                    ? componentProps.get("attachmentItemRequiredRules")
                    : linkageRules.get("attachmentItemRequiredRules");
            if (configured == null) {
                continue;
            }
            validateAttachmentItemRequiredRules(
                    field,
                    componentProps,
                    configured,
                    validProperties,
                    currentEntityFields);
        }
    }

    private void validateAttachmentItemRequiredRules(
            EntityFormField field,
            Map<String, Object> componentProps,
            Object configured,
            Set<String> validProperties,
            List<EntityField> currentEntityFields) {
        String fieldType = String.valueOf(field.getFieldType())
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!Set.of("FILE", "IMAGE").contains(fieldType)) {
            throw new IllegalArgumentException(
                    fieldLabel(field) + "不是文件或图片字段，不能配置附件项逻辑必填");
        }
        Map<String, Object> rules = mapValue(configured);
        if (integerValue(rules.get("version")) != 1) {
            throw new IllegalArgumentException(
                    fieldLabel(field) + "附件项逻辑必填仅支持 version=1");
        }
        if (!(componentProps.get("fileItems") instanceof List<?> fileItems)
                || fileItems.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldLabel(field) + "附件项逻辑必填缺少附件项发布快照");
        }
        Map<String, Map<String, Object>> itemsByKey =
                new LinkedHashMap<>();
        for (Object value : fileItems) {
            if (!(value instanceof Map<?, ?> itemValue)) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项发布快照格式不合法");
            }
            Map<String, Object> item = stringMap(itemValue);
            String itemKey = text(item.get("itemKey"));
            if (!ATTACHMENT_ITEM_KEY.matcher(itemKey).matches()) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项稳定标识不合法: " + itemKey);
            }
            if (!StringUtils.hasText(text(item.get("itemName")))) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项名称不能为空");
            }
            if (itemsByKey.putIfAbsent(itemKey, item) != null) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项稳定标识重复: " + itemKey);
            }
        }
        Map<String, EntityFieldFileItem> currentItemsByKey =
                currentEntityFields == null
                        ? Map.of()
                        : currentAttachmentItems(field, currentEntityFields);
        if (!(rules.get("items") instanceof List<?> configuredItems)
                || configuredItems.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldLabel(field) + "附件项逻辑必填至少需要一条规则");
        }
        Set<String> configuredKeys = new HashSet<>();
        for (Object value : configuredItems) {
            if (!(value instanceof Map<?, ?> configuredItemValue)) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项逻辑必填规则格式不合法");
            }
            Map<String, Object> configuredItem =
                    stringMap(configuredItemValue);
            String itemKey = text(configuredItem.get("itemKey"));
            Map<String, Object> snapshotItem = itemsByKey.get(itemKey);
            if (snapshotItem == null) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项逻辑必填引用不存在: " + itemKey);
            }
            if (!configuredKeys.add(itemKey)) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项逻辑必填重复配置: " + itemKey);
            }
            EntityFieldFileItem currentItem = currentItemsByKey.get(itemKey);
            if (currentEntityFields != null && currentItem == null) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项逻辑必填引用已失效: " + itemKey);
            }
            if (booleanValue(snapshotItem.get("required"))
                    || currentItem != null
                    && Boolean.TRUE.equals(currentItem.getRequired())) {
                throw new IllegalArgumentException(
                        fieldLabel(field) + "附件项“"
                                + text(snapshotItem.get("itemName"))
                                + "”已是实体固定必填，无需配置逻辑必填");
            }
            conditionEvaluator.validateStructured(
                    configuredItem.get("requiredConditionConfig"),
                    validProperties,
                    fieldLabel(field) + "附件项“"
                            + text(snapshotItem.get("itemName"))
                            + "”必填条件：");
        }
    }

    private Map<String, EntityFieldFileItem> currentAttachmentItems(
            EntityFormField formField,
            List<EntityField> entityFields) {
        EntityField entityField = entityFields.stream()
                .filter(field -> field != null && (
                        StringUtils.hasText(formField.getFieldId())
                                && formField.getFieldId().equals(field.getId())
                        || StringUtils.hasText(formField.getFieldCode())
                                && formField.getFieldCode().equals(
                                        field.getFieldCode())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        fieldLabel(formField) + "绑定的实体字段不存在"));
        List<EntityFieldFileItem> items = fileItemMapper.findByFieldId(
                entityField.getId());
        Map<String, EntityFieldFileItem> result = new LinkedHashMap<>();
        if (items != null) {
            for (EntityFieldFileItem item : items) {
                if (item != null && StringUtils.hasText(item.getItemKey())) {
                    result.put(item.getItemKey(), item);
                }
            }
        }
        return result;
    }

    private Set<String> validEntityProperties(
            EntityForm form,
            List<EntityField> entityFields) {
        Set<String> result = new HashSet<>();
        if (form.getFields() != null) {
            form.getFields().stream()
                    .filter(field -> field != null
                            && StringUtils.hasText(field.getFieldCode()))
                    .map(EntityFormField::getFieldCode)
                    .forEach(result::add);
        }
        if (entityFields != null) {
            entityFields.stream()
                    .filter(field -> field != null
                            && StringUtils.hasText(field.getFieldCode()))
                    .map(EntityField::getFieldCode)
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * 返回唯一性规则可绑定的真实存储字段。
     *
     * <p>不能复用 {@link #validEntityProperties(EntityForm, List)}：后者为
     * 条件必填兼容表单级字段，而唯一终检需要查询动态实体表，虚拟表单字段
     * 不具备可靠存储列。</p>
     */
    private Set<String> persistentEntityProperties(
            List<EntityField> entityFields) {
        if (entityFields == null || entityFields.isEmpty()) {
            return Set.of();
        }
        return entityFields.stream()
                .filter(field -> field != null
                        && StringUtils.hasText(field.getFieldCode()))
                .map(EntityField::getFieldCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value)
                || Integer.valueOf(1).equals(value)
                || "1".equals(String.valueOf(value));
    }

    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map
                ? stringMap(map) : Map.of();
    }

    private int integerValue(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private String fieldLabel(EntityFormField field) {
        if (StringUtils.hasText(field.getFieldLabel())) {
            return "字段“" + field.getFieldLabel() + "”";
        }
        if (StringUtils.hasText(field.getFieldName())) {
            return "字段“" + field.getFieldName() + "”";
        }
        return "字段“" + field.getFieldCode() + "”";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 校验字段校验规则：区间、长度、格式与正则表达式 */
    private void validateValidationRules(Map<String, Object> validation) {
        if (validation.isEmpty()) {
            return;
        }
        BigDecimal min = number(validation.get("min"));
        BigDecimal max = number(validation.get("max"));
        BigDecimal minLength = number(validation.get("minLength"));
        BigDecimal maxLength = number(validation.get("maxLength"));
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("字段校验最小值不能大于最大值");
        }
        if (minLength != null && maxLength != null && minLength.compareTo(maxLength) > 0) {
            throw new IllegalArgumentException("字段校验最小长度不能大于最大长度");
        }
        String format = String.valueOf(validation.getOrDefault("format", "")).toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) {
            throw new IllegalArgumentException("不支持的字段格式校验: " + format);
        }
        Object patternValue = validation.get("pattern");
        if (patternValue == null) {
            return;
        }
        if (!(patternValue instanceof String pattern)) {
            throw new IllegalArgumentException("字段校验正则必须为字符串");
        }
        if (pattern.length() > 500) {
            throw new IllegalArgumentException("字段校验正则不能超过 500 个字符");
        }
        if (!pattern.isEmpty()) {
            try {
                Pattern.compile(pattern);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "字段校验正则表达式语法不正确",
                        exception);
            }
        }
    }

    /** 校验扩展配置中各运行模式的可见/可编辑权限项是否合法 */
    private void validateModeAccess(Map<String, Object> extension) {
        Object modesValue = extension.get("modes");
        if (!(modesValue instanceof Map<?, ?> modes)) {
            return;
        }
        for (Map.Entry<?, ?> entry : modes.entrySet()) {
            String mode = String.valueOf(entry.getKey());
            if (!MODES.contains(mode)) {
                throw new IllegalArgumentException("不支持的表单运行模式: " + mode);
            }
            if (!(entry.getValue() instanceof Map<?, ?> access)) {
                throw new IllegalArgumentException("表单模式权限必须为对象: " + mode);
            }
            validateBoolean(access.get("visible"), "visible");
            validateBoolean(access.get("editable"), "editable");
        }
    }

    /** 校验权限配置项值必须为布尔或 null */
    private void validateBoolean(Object value, String key) {
        if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("字段模式配置 " + key + " 必须为布尔值");
        }
    }

    /** 将值解析为 BigDecimal，空或格式不合法时抛出 IllegalArgumentException */
    private BigDecimal number(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalArgumentException("字段校验数值配置不合法");
        }
    }

    /** 校验扩展组件标识格式 */
    private void validateExtensionName(String name, String label) {
        if (StringUtils.hasText(name) && !EXTENSION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(label + "标识不合法");
        }
    }

    /** 空白字符串转 null */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }
}
