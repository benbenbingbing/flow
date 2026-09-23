package com.workflow.process.assignment.entity;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code entityUserReferenceField} 人员解析器的不可变 V1 配置。
 *
 * @param schemaVersion 结构版本，保存在对象中供后续校验、查询或展示
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param fieldCode 字段编码，后续用于处理实体用户引用字段配置时定位或关联目标
 */
public record EntityUserReferenceFieldConfig(
        int schemaVersion,
        String entityCode,
        String fieldCode) {

    public static final String RESOLVER_CODE = "entityUserReferenceField";
    private static final int MAX_ENTITY_CODE_LENGTH = 128;
    private static final Pattern FIELD_CODE =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");

    /**
     * 从 BPMN extraParams 解析并严格校验静态坐标。
     *
     * @param extraParams 附加参数，供本方法解析实体用户引用字段配置时使用
     * @return 解析后的实体用户引用字段配置结果，供调用方继续处理
     */
    public static EntityUserReferenceFieldConfig parse(
            Map<String, Object> extraParams) {
        Map<String, Object> source = extraParams == null
                ? Map.of() : extraParams;
        Set<String> unsupported = new java.util.LinkedHashSet<>(
                source.keySet());
        unsupported.removeAll(Set.of(
                "schemaVersion", "entityCode", "fieldCode"));
        if (!unsupported.isEmpty()) {
            throw invalid("包含不支持的参数: "
                    + String.join(",", unsupported));
        }
        if (!source.containsKey("schemaVersion")) {
            throw invalid("schemaVersion 不能为空");
        }
        int schemaVersion = integer(source.get("schemaVersion"));
        if (schemaVersion != 1) {
            throw invalid("schemaVersion 只支持 1");
        }
        String entityCode = required(
                source.get("entityCode"), "entityCode");
        if (entityCode.length() > MAX_ENTITY_CODE_LENGTH) {
            throw invalid("entityCode 长度不能超过 "
                    + MAX_ENTITY_CODE_LENGTH);
        }
        String fieldCode = required(
                source.get("fieldCode"), "fieldCode");
        if (!FIELD_CODE.matcher(fieldCode).matches()) {
            throw invalid("fieldCode 格式不合法");
        }
        return new EntityUserReferenceFieldConfig(
                schemaVersion,
                entityCode,
                fieldCode);
    }

    /**
     * 校验普通任务与多实例使用的 assignmentMode。
     *
     * @param assignmentMode 分配模式标识，决定后续分配模式采用的处理分支
     * @param multiInstance 多实例，作为 {@code normalizedAssignmentMode} 的输入影响后续处理
     */
    public void validateAssignmentMode(
            String assignmentMode,
            boolean multiInstance) {
        String mode = normalizedAssignmentMode(
                assignmentMode, multiInstance);
        if (multiInstance) {
            if (!"CANDIDATE".equals(mode)
                    && !"MULTI_INSTANCE".equals(mode)) {
                throw invalid("多人办理只支持 CANDIDATE 或 MULTI_INSTANCE");
            }
            return;
        }
        if (!"DIRECT".equals(mode) && !"CANDIDATE".equals(mode)) {
            throw invalid("普通任务只支持 DIRECT 或 CANDIDATE");
        }
    }

    /**
     * 发布时以实体目录返回的真实单/多值属性约束分配语义。
     *
     * @param assignmentMode 分配模式标识，决定后续字段{@code cardinality}采用的处理分支
     * @param multiInstance 多实例，供本方法校验字段{@code cardinality}时使用
     * @param multiple {@code multiple}，作为 {@code invalid} 的输入影响后续处理
     */
    public void validateFieldCardinality(
            String assignmentMode,
            boolean multiInstance,
            boolean multiple) {
        if (multiInstance) {
            return;
        }
        String mode = normalizedAssignmentMode(assignmentMode, false);
        String requiredMode = multiple ? "CANDIDATE" : "DIRECT";
        if (!requiredMode.equals(mode)) {
            throw invalid(multiple
                    ? "多选用户关系字段必须使用 CANDIDATE"
                    : "单选用户关系字段必须使用 DIRECT");
        }
    }

    /**
     * 生成规范化分配模式文本，供后续匹配或展示。
     *
     * @param assignmentMode 分配模式标识，决定后续规范化分配模式采用的处理分支
     * @param multiInstance 多实例，供本方法处理规范化分配模式时使用
     * @return 处理后的规范化分配模式文本，供调用方比较或展示
     */
    private String normalizedAssignmentMode(
            String assignmentMode,
            boolean multiInstance) {
        return StringUtils.hasText(assignmentMode)
                ? assignmentMode.trim().toUpperCase(java.util.Locale.ROOT)
                : (multiInstance ? "CANDIDATE" : "DIRECT");
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private static int integer(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw invalid("schemaVersion 必须是整数");
        }
    }

    /**
     * 生成必填文本，供后续匹配或展示。
     *
     * @param value 待处理必填的原始输入，结果供调用方继续使用
     * @param field 字段，作为 {@code invalid} 的输入影响后续处理
     * @return 处理后的必填文本，供调用方比较或展示
     */
    private static String required(Object value, String field) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            throw invalid(field + " 不能为空");
        }
        return text;
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(
                RESOLVER_CODE + " 配置无效: " + message);
    }
}
