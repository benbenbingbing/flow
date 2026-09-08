package com.workflow.entity.form.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import java.util.*;

/** 将节点配置投影为渲染及提交校验所需的字段，不访问字段兼容表。 */
public final class EntityFormFieldProjection {
    private final JsonDocumentCodec codec;

    public EntityFormFieldProjection(JsonDocumentCodec codec) {
        this.codec = codec;
    }

    /** 草稿的字段完全由节点生成；发布快照可通过另一重载补充快照中的字段元数据。 */
    public List<EntityFormField> derive(String formId, List<EntityFormNode> nodes) {
        EntityForm form = new EntityForm();
        form.setId(formId);
        return derive(form, nodes);
    }

    /** 节点显式设置（包括清空值）优先；字段列表只用于补充已发布快照或实体字段元数据。 */
    public List<EntityFormField> derive(
            EntityForm form,
            List<EntityFormNode> publishedNodes) {
        List<EntityFormField> existing =
                form.getFields() == null ? List.of() : form.getFields();
        Map<String, EntityFormField> byId = new HashMap<>();
        Map<String, EntityFormField> byCode = new HashMap<>();
        existing.forEach(field -> {
            byId.put(field.getId(), field);
            if (StringUtils.hasText(field.getFieldCode())) {
                byCode.put(field.getFieldCode(), field);
            }
        });
        List<EntityFormField> runtimeFields = new ArrayList<>();
        int sortOrder = 0;
        for (EntityFormNode node : publishedNodes == null
                ? List.<EntityFormNode>of()
                : publishedNodes) {
            if (!Set.of("FIELD", "SUB_FORM", "REPEATER")
                    .contains(node.getNodeType())) {
                continue;
            }
            Map<String, Object> props = StringUtils.hasText(node.getPropsDocument())
                    ? codec.readObject(node.getPropsDocument(), "发布表单节点属性")
                    : Map.of();
            String fieldCode = text(props.getOrDefault("fieldCode", node.getNodeKey()));
            EntityFormField field = new EntityFormField();
            EntityFormField previous = byId.get(node.getId());
            if (previous == null) {
                previous = byCode.get(fieldCode);
            }
            if (previous != null) {
                BeanUtils.copyProperties(previous, field);
            }
            field.setId(node.getId());
            field.setFormId(form.getId());
            if (props.containsKey("fieldId")) {
                field.setFieldId(text(props.get("fieldId")));
            }
            field.setFieldCode(fieldCode);
            if (props.containsKey("fieldName")) {
                field.setFieldName(text(props.get("fieldName")));
            }
            if (!StringUtils.hasText(field.getFieldName())) {
                field.setFieldName(text(props.get("label")));
            }
            field.setFieldLabel(text(props.getOrDefault("label", field.getFieldName())));
            if (props.containsKey("fieldType")) {
                field.setFieldType(text(props.get("fieldType")));
            }
            if (!StringUtils.hasText(field.getFieldType())) {
                field.setFieldType(
                        Set.of("SUB_FORM", "REPEATER")
                                .contains(node.getNodeType())
                                ? "SUB_FORM"
                                : node.getNodeType());
            }
            if (props.containsKey("componentType")) {
                field.setComponentType(text(props.get("componentType")));
            }
            if (!StringUtils.hasText(field.getComponentType())) {
                field.setComponentType(
                        Set.of("SUB_FORM", "REPEATER")
                                .contains(node.getNodeType())
                                ? "sub_form"
                                : node.getNodeType().toLowerCase());
            }
            if (props.containsKey("placeholder")) {
                field.setPlaceholder(text(props.get("placeholder")));
            }
            if (props.containsKey("defaultValue")) {
                field.setDefaultValue(text(props.get("defaultValue")));
            }
            if (props.containsKey("gridSpan")) {
                field.setGridSpan(integer(props.get("gridSpan"), 24));
            } else if (field.getGridSpan() == null) {
                field.setGridSpan(24);
            }
            if (props.containsKey("required")) {
                field.setIsRequired(booleanFlag(props.get("required")));
            }
            if (props.containsKey("readonly")) {
                field.setIsReadonly(booleanFlag(props.get("readonly")));
            }
            if (props.containsKey("hidden")) {
                field.setIsHidden(booleanFlag(props.get("hidden")));
            }
            field.setSortOrder(sortOrder++);
            if (props.containsKey("componentProps")) {
                Object componentProps = props.get("componentProps");
                field.setComponentProps(componentProps == null
                        ? null : codec.write(componentProps, "发布字段组件属性"));
            }
            Map<String, Object> rules = StringUtils.hasText(node.getRulesDocument())
                    ? codec.readObject(node.getRulesDocument(), "发布表单节点规则")
                    : Map.of();
            if (rules.containsKey("validation")) {
                Object validation = rules.get("validation");
                field.setValidationRules(validation == null
                        ? null : codec.write(validation, "发布字段校验规则"));
            }
            if (rules.containsKey("extension")) {
                Object extension = rules.get("extension");
                field.setExtensionConfig(extension == null
                        ? null : codec.write(extension, "发布字段扩展配置"));
            }
            if (StringUtils.hasText(node.getDataSourceBindingsDocument())) {
                field.setDataSourceBindings(codec.readObject(
                        node.getDataSourceBindingsDocument(),
                        "发布字段数据源绑定"));
            }
            runtimeFields.add(field);
        }
        return runtimeFields.isEmpty() ? existing : runtimeFields;
    }


    /**
     * 导入或撤销草稿时，将快照中的字段补充到节点。已有节点属性优先，显式 null 表示清空。
     * 有字段节点时仅补齐属性，不把旧快照中已移出节点树的字段重新加入设计。
     */
    public List<EntityFormNode> materialize(
            String formId, List<EntityFormField> fields, List<EntityFormNode> nodes) {
        // 节点存在而快照字段为空时也先投影默认值，确保重复转换得到相同配置。
        EntityForm envelope = new EntityForm();
        envelope.setId(formId);
        envelope.setFields(fields);
        fields = derive(envelope, nodes);
        List<EntityFormNode> result = new ArrayList<>();
        Map<String, EntityFormField> byId = new HashMap<>();
        Map<String, EntityFormField> byCode = new HashMap<>();
        for (EntityFormField field : fields == null ? List.<EntityFormField>of() : fields) {
            byId.put(field.getId(), field);
            byCode.put(field.getFieldCode(), field);
        }
        boolean hasFields = false;
        for (EntityFormNode source : nodes == null ? List.<EntityFormNode>of() : nodes) {
            EntityFormNode node = new EntityFormNode();
            BeanUtils.copyProperties(source, node);
            if (Set.of("FIELD", "SUB_FORM", "REPEATER").contains(node.getNodeType())) {
                hasFields = true;
                Map<String, Object> props = codec.readObject(node.getPropsDocument(), "表单节点属性");
                EntityFormField field = byId.get(node.getId());
                if (field == null) field = byCode.get(text(props.getOrDefault("fieldCode", node.getNodeKey())));
                if (field != null) mergeField(node, field, props);
            }
            result.add(node);
        }
        if (!hasFields) {
            int order = 0;
            for (EntityFormField field : fields == null ? List.<EntityFormField>of() : fields) {
                if (!StringUtils.hasText(field.getFieldCode())) {
                    throw new IllegalArgumentException("表单字段缺少 fieldCode，无法转换为节点");
                }
                EntityFormNode node = new EntityFormNode();
                node.setId(StringUtils.hasText(field.getId()) ? field.getId()
                        : UUID.randomUUID().toString().replace("-", ""));
                node.setFormId(formId);
                node.setNodeKey(field.getFieldCode());
                node.setNodeType("SUB_FORM".equals(field.getFieldType()) ? "SUB_FORM" : "FIELD");
                node.setBindingType("ENTITY_FIELD");
                node.setBindingRef(field.getFieldCode());
                node.setOrderKey((long) ++order * 1024);
                node.setRevision(1);
                node.setDeleted(0);
                mergeField(node, field, Map.of());
                result.add(node);
            }
        }
        return result;
    }

    /** 转换仅发生在输入边界，不会回写或修改不可变发布快照。 */
    private void mergeField(EntityFormNode node, EntityFormField field, Map<String, Object> overrides) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fieldId", field.getFieldId());
        props.put("fieldCode", field.getFieldCode());
        props.put("fieldName", field.getFieldName());
        props.put("label", field.getFieldLabel() == null ? field.getFieldName() : field.getFieldLabel());
        props.put("fieldType", field.getFieldType());
        props.put("componentType", field.getComponentType());
        props.put("required", Integer.valueOf(1).equals(field.getIsRequired()));
        props.put("readonly", Integer.valueOf(1).equals(field.getIsReadonly()));
        props.put("hidden", Integer.valueOf(1).equals(field.getIsHidden()));
        props.put("defaultValue", field.getDefaultValue());
        props.put("placeholder", field.getPlaceholder());
        props.put("gridSpan", field.getGridSpan());
        if (StringUtils.hasText(field.getComponentProps())) {
            props.put("componentProps", codec.readObject(field.getComponentProps(), "表单组件属性"));
        }
        props.putAll(overrides);
        node.setPropsDocument(codec.write(props, "表单节点属性"));
        Map<String, Object> rules = new LinkedHashMap<>();
        if (StringUtils.hasText(field.getValidationRules())) {
            rules.put("validation", codec.readTree(field.getValidationRules(), "表单校验规则"));
        }
        if (StringUtils.hasText(field.getExtensionConfig())) {
            rules.put("extension", codec.readTree(field.getExtensionConfig(), "表单扩展配置"));
        }
        rules.putAll(codec.readObject(node.getRulesDocument(), "表单节点规则"));
        node.setRulesDocument(codec.write(rules, "表单节点规则"));
        if (!StringUtils.hasText(node.getDataSourceBindingsDocument()) && field.getDataSourceBindings() != null) {
            node.setDataSourceBindingsDocument(codec.write(field.getDataSourceBindings(), "表单数据源绑定"));
        }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Integer integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException exception) { return fallback; }
    }
    private Integer booleanFlag(Object value) { return Boolean.TRUE.equals(value) ? 1 : 0; }
}
