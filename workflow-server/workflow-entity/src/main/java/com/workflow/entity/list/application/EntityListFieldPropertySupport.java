package com.workflow.entity.list.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Applies and compares mutable list-field properties in one place. */
@Component
final class EntityListFieldPropertySupport {
    void copyMutable(EntityListField source, EntityListField target, Set<String> clearFields) {
        if (source.getFieldId() != null) target.setFieldId(source.getFieldId());
        if (source.getFieldCode() != null) target.setFieldCode(source.getFieldCode());
        if (source.getFieldName() != null) target.setFieldName(source.getFieldName());
        if (source.getSortOrder() != null) target.setSortOrder(source.getSortOrder());
        if (source.getOrderKey() != null) target.setOrderKey(source.getOrderKey());
        if (source.getWidth() != null) target.setWidth(source.getWidth());
        if (source.getShowInList() != null) target.setShowInList(source.getShowInList());
        if (source.getIsQuery() != null) target.setIsQuery(source.getIsQuery());
        if (source.getQueryType() != null) target.setQueryType(source.getQueryType());
        if (source.getAlign() != null) target.setAlign(source.getAlign());
        if (source.getDataSourceType() != null) target.setDataSourceType(source.getDataSourceType());
        if (source.getDataSourceConfig() != null) target.setDataSourceConfig(source.getDataSourceConfig());
        setOrClear(source.getDataSourceId(), clearFields, "dataSourceId", target::setDataSourceId);
        if (source.getRenderComponent() != null) target.setRenderComponent(source.getRenderComponent());
        if (source.getFormatter() != null) target.setFormatter(source.getFormatter());
        if (source.getColumnConfig() != null) target.setColumnConfig(source.getColumnConfig());
        if (source.getQueryConfig() != null) target.setQueryConfig(source.getQueryConfig());
        if (source.getRenderConfig() != null) target.setRenderConfig(source.getRenderConfig());
        setOrClear(source.getTemplateId(), clearFields, "templateId", target::setTemplateId);
        setOrClear(source.getTemplateVersion(), clearFields, "templateVersion", target::setTemplateVersion);
        setOrClear(source.getLocalOverridesDocument(), clearFields, "localOverridesDocument",
                target::setLocalOverridesDocument);
    }
    void setColumns(UpdateWrapper<EntityListField> wrapper, EntityListField field) {
        wrapper.set("field_id", field.getFieldId()).set("field_code", field.getFieldCode())
                .set("field_name", field.getFieldName()).set("sort_order", field.getSortOrder())
                .set("order_key", field.getOrderKey()).set("width", field.getWidth())
                .set("show_in_list", field.getShowInList()).set("is_query", field.getIsQuery())
                .set("query_type", field.getQueryType()).set("align", field.getAlign())
                .set("data_source_type", field.getDataSourceType())
                .set("data_source_config", field.getDataSourceConfig())
                .set("data_source_id", field.getDataSourceId())
                .set("render_component", field.getRenderComponent()).set("formatter", field.getFormatter())
                .set("column_config", field.getColumnConfig()).set("query_config", field.getQueryConfig())
                .set("render_config", field.getRenderConfig()).set("template_id", field.getTemplateId())
                .set("template_version", field.getTemplateVersion())
                .set("local_overrides_document", field.getLocalOverridesDocument())
                .set("revision", field.getRevision()).set("update_time", field.getUpdatedAt());
    }
    boolean same(EntityListField left, EntityListField right) {
        return Objects.equals(left.getFieldId(), right.getFieldId())
                && Objects.equals(left.getFieldCode(), right.getFieldCode())
                && Objects.equals(left.getFieldName(), right.getFieldName())
                && Objects.equals(left.getSortOrder(), right.getSortOrder())
                && Objects.equals(left.getOrderKey(), right.getOrderKey())
                && Objects.equals(left.getWidth(), right.getWidth())
                && Objects.equals(left.getShowInList(), right.getShowInList())
                && Objects.equals(left.getIsQuery(), right.getIsQuery())
                && Objects.equals(left.getQueryType(), right.getQueryType())
                && Objects.equals(left.getAlign(), right.getAlign())
                && Objects.equals(left.getDataSourceType(), right.getDataSourceType())
                && Objects.equals(left.getDataSourceConfig(), right.getDataSourceConfig())
                && Objects.equals(left.getDataSourceId(), right.getDataSourceId())
                && Objects.equals(left.getRenderComponent(), right.getRenderComponent())
                && Objects.equals(left.getFormatter(), right.getFormatter())
                && Objects.equals(left.getColumnConfig(), right.getColumnConfig())
                && Objects.equals(left.getQueryConfig(), right.getQueryConfig())
                && Objects.equals(left.getRenderConfig(), right.getRenderConfig())
                && Objects.equals(left.getTemplateId(), right.getTemplateId())
                && Objects.equals(left.getTemplateVersion(), right.getTemplateVersion())
                && Objects.equals(left.getLocalOverridesDocument(), right.getLocalOverridesDocument());
    }
    private <T> void setOrClear(T value, Set<String> clearFields, String key,
                                java.util.function.Consumer<T> setter) {
        if (clearFields.contains(key)) setter.accept(null);
        else if (value != null) setter.accept(value);
    }
}
