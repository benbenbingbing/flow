package com.workflow.service.config;

import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EntityFormConfigurationValidator {

    private static final Pattern FORM_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,99}");
    private static final Pattern FIELD_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Pattern EXTENSION_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");
    private static final Set<String> MODES = Set.of("create", "edit", "approve", "view");
    private static final Set<String> FORMATS = Set.of("", "EMAIL", "PHONE", "URL");

    private final StructuredConfigValidator structuredConfigValidator;

    public void validateForm(EntityForm form) {
        if (form == null || !StringUtils.hasText(form.getEntityId())) {
            throw new IllegalArgumentException("表单实体不能为空");
        }
        if (!StringUtils.hasText(form.getFormName())) {
            throw new IllegalArgumentException("表单名称不能为空");
        }
        if (!StringUtils.hasText(form.getFormKey()) || !FORM_KEY.matcher(form.getFormKey()).matches()) {
            throw new IllegalArgumentException("表单标识只能包含字母、数字、下划线和短横线，且必须以字母开头");
        }
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
        structuredConfigValidator.parseObject(form.getViewConfig(), "表单视图配置");
        form.setViewConfig(blankToNull(form.getViewConfig()));
        structuredConfigValidator.parseObject(form.getInitConfig(), "表单初始化配置");
        structuredConfigValidator.parseObject(
                form.getDataSourceBindingsDocument(),
                "表单级数据源绑定");
        form.setDataSourceBindingsDocument(
                blankToNull(
                        form.getDataSourceBindingsDocument()));
        validateFields(form.getFields());
    }

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
    }

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
    }

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

    private void validateBoolean(Object value, String key) {
        if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("字段模式配置 " + key + " 必须为布尔值");
        }
    }

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

    private void validateExtensionName(String name, String label) {
        if (StringUtils.hasText(name) && !EXTENSION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(label + "标识不合法");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
