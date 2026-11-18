package com.workflow.entity.definition.application;

import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 选择字段绑定唯一候选来源；外部编码作为业务值存储，不依赖外部接口的可用性。 */
public final class EntitySelectionOptionPolicy {
    public static final int EXTERNAL_MULTI_STORAGE_LENGTH = 2000;
    private static final java.util.regex.Pattern EXTERNAL_CODE =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}");
    private static final Set<String> SELECTION_TYPES = Set.of(
            "SELECT", "MULTI_SELECT", "SELECT_MULTIPLE", "RADIO", "CHECKBOX");

    private EntitySelectionOptionPolicy() {}

    /** 覆盖实体整包保存入口，防止绕过单字段接口继续写入旧格式。 */
    public static void validateSubmitted(List<EntityFieldDTO> fields) {
        if (fields != null) fields.forEach(EntitySelectionOptionPolicy::validateSubmitted);
    }

    /** 旧输入属性只用于报错，不作转换或兜底；调用方应先让用户绑定代码表。 */
    public static void validateSubmitted(EntityFieldDTO field) {
        if (field == null) return;
        if (StringUtils.hasText(field.getOptionsJson())
                || field.getOptions() != null && !field.getOptions().isEmpty()) {
            throw new IllegalArgumentException("字段「" + field.getFieldName()
                    + "」不再支持内嵌选项，请移除旧配置并绑定系统代码表");
        }
        field.setOptionsInterfaceId(StringUtils.hasText(field.getOptionsInterfaceId())
                ? field.getOptionsInterfaceId().trim() : null);
        if (!StringUtils.hasText(field.getDictType())) field.setDictType(null);
        requireSource(field.getFieldType(), field.getDictType(), field.getOptionsInterfaceId(), field.getFieldName());
    }

    /** 发布直接读取数据库草稿时同样核对绑定，不能用历史选项继续发布新版本。 */
    public static void validatePublished(EntityField field) {
        requireSource(field.getFieldType(), field.getDictType(), field.getOptionsInterfaceId(), field.getFieldName());
    }

    private static void requireSource(EntityField.FieldType type, String dictType, String interfaceId, String name) {
        boolean selection = type != null && SELECTION_TYPES.contains(type.name());
        if (StringUtils.hasText(interfaceId)) {
            if (!selection || StringUtils.hasText(dictType) || interfaceId.length() > 64) {
                throw new IllegalArgumentException("字段「" + name + "」只能绑定一种选项来源，且只允许选择字段绑定选项接口");
            }
        } else if (selection && !StringUtils.hasText(dictType)) {
            throw new IllegalArgumentException("字段「" + name + "」必须绑定系统代码表或选项接口");
        }
    }

    /** 接口多选存储 JSON 数组而非代码项关联，单项长度和数组总长度须能原样落库。 */
    public static boolean isExternalMultiple(EntityField field) {
        return StringUtils.hasText(field.getOptionsInterfaceId())
                && (field.getFieldType() == EntityField.FieldType.MULTI_SELECT
                    || field.getFieldType() == EntityField.FieldType.CHECKBOX);
    }

    /** 只检查提交值的形状和长度；候选随上下文变化，保存不重复读取外部接口。 */
    public static void validateExternalValue(EntityField field, Object value) {
        if (value == null) return;
        if (!isExternalMultiple(field)) {
            int limit = field.getFieldLength() == null || field.getFieldLength() <= 0
                    ? 128 : field.getFieldLength();
            if (value instanceof String code && (code.isEmpty()
                    || (code.length() <= limit && EXTERNAL_CODE.matcher(code).matches()))) return;
        } else if (value instanceof List<?> codes && codes.size() <= 50) {
            Set<String> unique = new java.util.HashSet<>();
            int length = 2;
            for (Object item : codes) {
                if (!(item instanceof String code) || !EXTERNAL_CODE.matcher(code).matches() || !unique.add(code)) {
                    throw invalidExternalValue(field);
                }
                length += code.length() + 3;
            }
            if (length - (codes.isEmpty() ? 0 : 1) <= EXTERNAL_MULTI_STORAGE_LENGTH) return;
        }
        throw invalidExternalValue(field);
    }

    private static IllegalArgumentException invalidExternalValue(EntityField field) {
        return new IllegalArgumentException("字段「" + field.getFieldName() + "」的外部编码格式、长度或数量不符合要求");
    }

    /** 表单节点及旧字段保存、发布共用：拒绝直接候选，动态数据源绑定另行校验。 */
    public static void rejectEmbeddedOptions(String type, Map<String, Object> props, String name) {
        if (!SELECTION_TYPES.contains(String.valueOf(type).toUpperCase(java.util.Locale.ROOT))) return;
        if (hasEmbeddedOptions(props)) {
            throw new IllegalArgumentException("字段「" + name
                    + "」不再支持内嵌选项，请改用系统代码表或动态数据源");
        }
    }

    private static boolean hasEmbeddedOptions(Map<String, Object> props) {
        Object options = props.get("options");
        Object json = props.get("optionsJson");
        boolean configured = options instanceof List<?> list ? !list.isEmpty() : options != null;
        return configured || json != null && StringUtils.hasText(String.valueOf(json));
    }
}
