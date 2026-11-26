package com.workflow.entity.ui.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 实体绑定是唯一事实来源；把选项绑定投影到表单快照以沿用已有的发布授权和执行。 */
@Component
@RequiredArgsConstructor
public class EntityFieldOptionInterfaces {
    private static final String OPTIONS = "FIELD_OPTIONS";

    private final EntityFieldMapper fields;
    private final UiExtensionDefinitionMapper interfaces;
    private final EntityDefinitionMapper entities;
    private final JsonDocumentCodec codec;

    /** 保存、发布只校验接口的定义与权限范围，不实际取数。 */
    public void requireAvailable(String entityId, String interfaceId) {
        if (!StringUtils.hasText(interfaceId)) return;
        var definition = interfaces.selectById(interfaceId);
        if (definition == null || !"INTERFACE".equals(definition.getExtensionType())
                || !"ACTIVE".equals(definition.getStatus())
                || !"READ".equals(definition.getInterfaceKind())
                || !"FORM".equals(definition.getInterfaceContextType())
                || !"REGISTERED_PROVIDER".equals(definition.getSourceType())
                || !definition.matchesScope(entityId, "ENTITY", entityId)
                || !StringUtils.hasText(definition.getOutputSchemaDocument())
                || !"array".equalsIgnoreCase(String.valueOf(codec.readObject(
                        definition.getOutputSchemaDocument(), "选项接口输出 Schema").get("type")))) {
            throw new IllegalArgumentException("选项接口必须是覆盖该实体的已启用 FORM 只读 Provider，且输出为数组");
        }
    }

    /** 将实体选项接口及动态实体的内置状态选项投影到预览和待发布节点。 */
    public List<EntityFormNode> inherit(String entityId, List<EntityFormNode> nodes) {
        if (nodes == null || nodes.isEmpty() || !StringUtils.hasText(entityId)) return nodes;
        List<EntityField> entityFields = fields.findByEntityId(entityId);
        boolean hasStatus = entityFields.stream().filter(EntityFieldOptionInterfaces::isSystemStatus)
                .anyMatch(field -> nodes.stream().anyMatch(node -> bindsField(node, field)));
        EntityDefinition entity = hasStatus ? entities.selectById(entityId) : null;
        // 系统表也可能有 status 列。只有动态实体的标准字段读取实体业务状态目录。
        boolean dynamicStatus = entity != null && entity.getStorageMode() == EntityDefinition.StorageMode.DYNAMIC;
        List<EntityField> configured = entityFields.stream()
                .filter(field -> StringUtils.hasText(field.getOptionsInterfaceId())
                        || (dynamicStatus && isSystemStatus(field)))
                .toList();
        if (configured.isEmpty()) return nodes;
        return nodes.stream().map(node -> inherit(node, configured, dynamicStatus)).toList();
    }

    private static boolean isSystemStatus(EntityField field) {
        return Boolean.TRUE.equals(field.getIsSystem()) && "status".equals(field.getFieldCode());
    }

    private static boolean bindsField(EntityFormNode node, EntityField field) {
        return "FIELD".equals(node.getNodeType()) && "ENTITY_FIELD".equals(node.getBindingType())
                && StringUtils.hasText(node.getBindingRef())
                && (Objects.equals(field.getId(), node.getBindingRef())
                    || Objects.equals(field.getFieldCode(), node.getBindingRef()));
    }

    private EntityFormNode inherit(EntityFormNode source, List<EntityField> fields, boolean dynamicStatus) {
        EntityField field = fields.stream()
                .filter(item -> bindsField(source, item))
                .findFirst().orElse(null);
        if (field == null) return source;
        Map<String, Object> bindings = StringUtils.hasText(source.getDataSourceBindingsDocument())
                ? new LinkedHashMap<>(codec.readObject(source.getDataSourceBindingsDocument(), "表单节点数据源"))
                : new LinkedHashMap<>();
        boolean systemStatus = dynamicStatus && isSystemStatus(field);
        Map<String, Object> inherited = Map.of("extensionId", systemStatus
                ? EntityStatusOptionsProvider.EXTENSION_ID : field.getOptionsInterfaceId());
        if (!systemStatus && bindings.containsKey(OPTIONS) && !inherited.equals(bindings.get(OPTIONS))) {
            throw new IllegalArgumentException("字段「" + field.getFieldName() + "」继承实体选项接口，请删除表单上的字段选项绑定");
        }
        Map<String, Object> props = StringUtils.hasText(source.getPropsDocument())
                ? codec.readObject(source.getPropsDocument(), "表单节点属性") : Map.of();
        String component = String.valueOf(props.getOrDefault("componentType", ""));
        if (!systemStatus && !component.isBlank() && !List.of("select", "select_multiple", "multi_select", "radio", "checkbox")
                .contains(component.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("字段「" + field.getFieldName() + "」必须使用单选或多选组件以继承实体选项接口");
        }
        bindings.put(OPTIONS, inherited);
        EntityFormNode node = new EntityFormNode();
        BeanUtils.copyProperties(source, node);
        if (systemStatus) {
            // 重发旧文本节点时在新快照中统一使用单选，沿用实体字段的写保护。
            Map<String, Object> statusProps = new LinkedHashMap<>(props);
            statusProps.put("componentType", "select");
            if (Boolean.FALSE.equals(field.getEditable())) statusProps.put("readonly", true);
            node.setPropsDocument(codec.write(statusProps, "系统状态节点属性"));
        }
        node.setDataSourceBindingsDocument(codec.write(bindings, "继承实体选项接口"));
        return node;
    }
}
