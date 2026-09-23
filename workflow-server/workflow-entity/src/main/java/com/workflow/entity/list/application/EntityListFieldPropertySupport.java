package com.workflow.entity.list.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Applies and compares mutable list-field properties in one place. */
@Component
final class EntityListFieldPropertySupport {
    /**
     * 复制可变；结果供后续流程传递或持久化。
     *
     * @param source 待复制可变的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法复制可变时使用
     * @param clearFields {@code clear}字段，供本方法复制可变时使用
     */
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
        setOrClear(source.getInterfaceExtensionId(), clearFields,
                "interfaceExtensionId", target::setInterfaceExtensionId);
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
    /**
     * 设置列集合；后续读取或执行将使用更新后的状态。
     *
     * @param wrapper {@code wrapper}，供本方法设置列集合时使用
     * @param field 字段，作为 {@code wrapper.set} 的输入影响后续处理
     */
    void setColumns(UpdateWrapper<EntityListField> wrapper, EntityListField field) {
        wrapper.set("field_id", field.getFieldId()).set("field_code", field.getFieldCode())
                .set("field_name", field.getFieldName()).set("sort_order", field.getSortOrder())
                .set("order_key", field.getOrderKey()).set("width", field.getWidth())
                .set("show_in_list", field.getShowInList()).set("is_query", field.getIsQuery())
                .set("query_type", field.getQueryType()).set("align", field.getAlign())
                .set("data_source_type", field.getDataSourceType())
                .set("data_source_config", field.getDataSourceConfig())
                .set("interface_extension_id", field.getInterfaceExtensionId())
                .set("render_component", field.getRenderComponent()).set("formatter", field.getFormatter())
                .set("column_config", field.getColumnConfig()).set("query_config", field.getQueryConfig())
                .set("render_config", field.getRenderConfig()).set("template_id", field.getTemplateId())
                .set("template_version", field.getTemplateVersion())
                .set("local_overrides_document", field.getLocalOverridesDocument())
                .set("revision", field.getRevision()).set("update_time", field.getUpdatedAt());
    }
    /**
     * 判断相同条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同时使用
     * @param right 右侧，供本方法处理相同时使用
     * @return 相同条件成立时为 true，否则为 false
     */
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
                && Objects.equals(left.getInterfaceExtensionId(), right.getInterfaceExtensionId())
                && Objects.equals(left.getRenderComponent(), right.getRenderComponent())
                && Objects.equals(left.getFormatter(), right.getFormatter())
                && Objects.equals(left.getColumnConfig(), right.getColumnConfig())
                && Objects.equals(left.getQueryConfig(), right.getQueryConfig())
                && Objects.equals(left.getRenderConfig(), right.getRenderConfig())
                && Objects.equals(left.getTemplateId(), right.getTemplateId())
                && Objects.equals(left.getTemplateVersion(), right.getTemplateVersion())
                && Objects.equals(left.getLocalOverridesDocument(), right.getLocalOverridesDocument());
    }
    /**
     * 设置或{@code clear}；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置或{@code clear}的原始输入，结果供调用方继续使用
     * @param clearFields {@code clear}字段，供本方法设置或{@code clear}时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param setter {@code setter}，供本方法设置或{@code clear}时使用
     */
    private <T> void setOrClear(T value, Set<String> clearFields, String key,
                                java.util.function.Consumer<T> setter) {
        if (clearFields.contains(key)) setter.accept(null);
        else if (value != null) setter.accept(value);
    }
}
