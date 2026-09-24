package com.workflow.entity.list.application;

import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** 列表过滤和排序只使用实体列；扩展值在数据库分页后计算，不能改变总数或页边界。 */
public final class EntityListQueryPolicy {
    private EntityListQueryPolicy() {}

    /** 接口覆盖和虚拟身份即使声明 ENTITY_FIELD，也不能伪装成可查询的物理字段。 */
    public static boolean isEntityField(EntityListField field) {
        return field != null
                && (!StringUtils.hasText(field.getDataSourceType())
                    || "ENTITY_FIELD".equalsIgnoreCase(field.getDataSourceType().trim()))
                && !StringUtils.hasText(field.getInterfaceExtensionId())
                && (field.getFieldId() == null || !field.getFieldId().startsWith("virtual_"));
    }

    /**
     * 保存、发布和读取旧发布版时使用相同规则。旧配置必须经正常编辑/发布修正，
     * 不能静默忽略筛选，或重新启用全量读取来兼容。
     */
    public static void validateConfiguration(List<EntityListField> fields) {
        if (fields == null) return;
        for (EntityListField field : fields) {
            if (field != null && Boolean.TRUE.equals(field.getIsQuery()) && !isEntityField(field)) {
                throw new IllegalArgumentException("虚拟列仅用于展示，请取消查询条件并重新发布: " + field.getFieldCode());
            }
        }
    }

    /** 检查用户、页面参数和固定条件，包含运算符与范围边界，避免直接 API 请求绕过设计器。 */
    public static void validateFilters(List<EntityListField> fields, Map<String, ?> filters) {
        if (fields == null || filters == null) return;
        for (String key : filters.keySet()) {
            String code = fields.stream().anyMatch(field -> field != null && Objects.equals(key, field.getFieldCode()))
                    ? key : baseField(key);
            requireEntityField(fields, code, "查询条件");
        }
    }

    /** 校验扩展列排序；实体字段是否存在及其 SQL 标识符由元数据校验器和查询服务继续校验。 */
    public static void validateSort(List<EntityListField> fields, String fieldCode) {
        if (StringUtils.hasText(fieldCode)) requireEntityField(fields, fieldCode, "排序字段");
    }

    private static void requireEntityField(List<EntityListField> fields, String code, String usage) {
        if (fields == null) return;
        for (EntityListField field : fields) {
            if (field != null && Objects.equals(code, field.getFieldCode()) && !isEntityField(field)) {
                throw new IllegalArgumentException("虚拟列不能作为" + usage + ": " + code);
            }
        }
    }

    private static String baseField(String key) {
        if (key == null) throw new IllegalArgumentException("查询字段不能为空");
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) return key.substring(0, key.length() - suffix.length());
        }
        return key;
    }
}
