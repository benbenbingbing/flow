package com.workflow.entity.list.application;

import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.core.serialization.JsonDocumentCodec;
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

    /**
     * 校验表头排序权限。调用方必须传入同一次查询所用的发布字段，不能使用设计器草稿。
     * 默认排序不走该入口，因此旧列表可继续按未开放交互排序的字段排序；清空两项则恢复默认。
     * 未开放、隐藏、虚拟列或非法方向均在查询前拒绝，防止直接 API 绕过表头开关。
     */
    public static void validateRequestedSort(List<EntityListField> fields, String fieldCode,
            String direction, JsonDocumentCodec codec) {
        if (!StringUtils.hasText(fieldCode)) {
            if (StringUtils.hasText(direction)) throw new IllegalArgumentException("排序方向必须同时指定排序字段");
            return;
        }
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("排序方向仅支持 ASC 或 DESC");
        }
        validateSort(fields, fieldCode);
        EntityListField field = fields == null ? null : fields.stream()
                .filter(item -> item != null && Objects.equals(fieldCode, item.getFieldCode()))
                .findFirst().orElse(null);
        if (field == null || Boolean.FALSE.equals(field.getShowInList())
                || !StringUtils.hasText(field.getColumnConfig())
                || !Boolean.TRUE.equals(codec.readObject(field.getColumnConfig(), "列展示配置").get("sortable"))) {
            throw new IllegalArgumentException("字段未开启列排序: " + fieldCode);
        }
    }

    /** 保存时只接受布尔开关；扩展值在分页之后生成，不能声明可进行数据库排序。 */
    public static void validateColumnSort(EntityListField field, Map<String, Object> columnConfig) {
        Object sortable = columnConfig.get("sortable");
        if (sortable != null && !(sortable instanceof Boolean)) {
            throw new IllegalArgumentException("是否排序必须为布尔值: " + field.getFieldCode());
        }
        if (Boolean.TRUE.equals(sortable) && !isEntityField(field)) {
            throw new IllegalArgumentException("虚拟列不能开启列排序: " + field.getFieldCode());
        }
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
