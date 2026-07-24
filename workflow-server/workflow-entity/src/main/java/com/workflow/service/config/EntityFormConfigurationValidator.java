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

/**
 * 实体表单配置校验器
 * 
 * 对表单及表单字段配置进行保存前的合法性校验：表单标识/名称、自定义组件版本锁定、
 * 字段编码唯一性与格式、字段校验规则（min/max、minLength/maxLength、format）、
 * 字段在不同运行模式（create/edit/approve/view）下的可见与可编辑权限、栅格宽度等。
 */
@Component
@RequiredArgsConstructor
public class EntityFormConfigurationValidator {

    /** 表单标识正则：字母开头，字母数字下划线短横线，长度 1~100 */
    private static final Pattern FORM_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,99}");
    /** 字段编码正则：字母开头，字母数字下划线，长度 1~100 */
    private static final Pattern FIELD_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    /** 扩展组件标识正则：字母开头，字母数字下划线点短横线，长度 1~100 */
    private static final Pattern EXTENSION_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");
    /** 支持的表单运行模式 */
    private static final Set<String> MODES = Set.of("create", "edit", "approve", "view");
    /** 支持的字段格式校验类型 */
    private static final Set<String> FORMATS = Set.of("", "EMAIL", "PHONE", "URL");

    private final StructuredConfigValidator structuredConfigValidator;

    /**
     * 校验表单整体配置。
     *
     * @param form 表单对象（含字段列表）
     * @throws IllegalArgumentException 实体为空、名称为空、标识不合法、组件版本未锁定等校验失败时抛出
     */
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

    /** 校验字段校验规则：min/max、minLength/maxLength 区间合理性及 format 取值 */
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
}
