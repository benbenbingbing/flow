package com.workflow.entity.definition.application;

import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 选择字段只保存代码表绑定；历史内嵌数据留在原存储中，但不能再次保存或发布。 */
public final class EntitySelectionOptionPolicy {
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
        requireDictionary(field.getFieldType(), field.getDictType(), field.getFieldName());
    }

    /** 发布直接读取数据库草稿时同样核对绑定，不能用历史选项继续发布新版本。 */
    public static void validatePublished(EntityField field) {
        requireDictionary(field.getFieldType(), field.getDictType(), field.getFieldName());
    }

    private static void requireDictionary(EntityField.FieldType type, String dictType, String name) {
        if (type != null && SELECTION_TYPES.contains(type.name()) && !StringUtils.hasText(dictType)) {
            throw new IllegalArgumentException("字段「" + name + "」必须绑定系统代码表，请重新配置后保存或发布");
        }
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
