package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.response.UiAvailableInterface;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.contracts.ui.UiDataSourceUsages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 查询指定表单、列表或实体绑定位置可选择的完整接口扩展。
 */
@Service
@RequiredArgsConstructor
public class UiAvailableInterfaceService {

    /** 只允许选择 READ 操作的绑定位置编码。 */
    private static final Set<String> READ_BINDINGS = Set.of(
            UiDataSourceUsages.FORM_INIT,
            UiDataSourceUsages.FIELD_OPTIONS,
            UiDataSourceUsages.FIELD_DEFAULT,
            UiDataSourceUsages.FIELD_COMPUTE,
            UiDataSourceUsages.SUBFORM_ROWS,
            UiDataSourceUsages.AFTER_LOAD,
            UiDataSourceUsages.LIST_QUERY,
            UiDataSourceUsages.LIST_COLUMN,
            UiDataSourceUsages.LIST_LOAD,
            UiDataSourceUsages.LIST_EXPORT,
            UiDataSourceUsages.DETAIL_LOAD,
            UiDataSourceUsages.FORM_OPEN,
            UiDataSourceUsages.SUBFORM_LOAD,
            UiDataSourceUsages.ENTITY_SELECTED);
    /** 只允许选择 WRITE 操作的绑定位置编码。 */
    private static final Set<String> WRITE_BINDINGS = Set.of(
            UiDataSourceUsages.BEFORE_SUBMIT,
            UiDataSourceUsages.DATA_CREATE,
            UiDataSourceUsages.DATA_UPDATE,
            UiDataSourceUsages.DATA_DELETE,
            UiDataSourceUsages.DATA_BATCH_DELETE,
            UiDataSourceUsages.FORM_SAVE,
            UiDataSourceUsages.SUBFORM_SAVE);

    /** 接口服务定义查询入口。 */
    private final UiExtensionDefinitionMapper sourceMapper;
    /** 实体定义查询入口，用于解析作用域所属实体。 */
    private final EntityDefinitionMapper definitionMapper;
    /** 表单配置查询入口，用于解析表单及其所属实体。 */
    private final EntityFormMapper formMapper;
    /** 列表配置查询入口，用于解析列表及其所属实体。 */
    private final EntityListConfigMapper listMapper;
    /** 接口 Schema 文档解析器。 */
    private final ObjectMapper objectMapper;
    /** 当前实体、表单或列表的配置访问校验服务。 */
    private final UiConfigurationAccessService configurationAccessService;

    public List<UiAvailableInterface> available(
            String ownerType,
            String ownerId,
            String bindingCode) {
        Owner owner = resolveOwner(ownerType, ownerId);
        String normalizedBinding = normalize(bindingCode);
        List<UiExtensionDefinition> definitions = sourceMapper.selectList(
                new LambdaQueryWrapper<UiExtensionDefinition>()
                        .eq(UiExtensionDefinition::getExtensionType,
                                "INTERFACE")
                        .eq(UiExtensionDefinition::getStatus, "ACTIVE")
                        .eq(UiExtensionDefinition::getDeleted, 0)
                        .orderByAsc(
                                UiExtensionDefinition::getExtensionKey));
        List<UiAvailableInterface> result = new ArrayList<>();
        for (UiExtensionDefinition definition : definitions) {
            if (isInvalidGlobalExtension(definition)
                    || !scopeMatches(definition, owner)) {
                continue;
            }
            String contextType = normalize(
                    definition.getInterfaceContextType());
            String kind = normalize(definition.getInterfaceKind());
            if (!allowedOperationContexts(
                    owner, normalizedBinding).contains(contextType)) {
                continue;
            }
            if (READ_BINDINGS.contains(normalizedBinding)
                    && !"READ".equals(kind)) {
                continue;
            }
            if (WRITE_BINDINGS.contains(normalizedBinding)
                    && !"WRITE".equals(kind)) {
                continue;
            }
            if (!schemaMatches(normalizedBinding, definition)) {
                continue;
            }
            result.add(new UiAvailableInterface(
                    definition.getId(),
                    definition.getExtensionKey(),
                    definition.getDisplayName(),
                    definition.getImplementationType(),
                    definition.getProviderCode(),
                    definition.getScopeType(),
                    definition.getScopeId(),
                    kind,
                    contextType,
                    readSchema(definition.getInputSchemaDocument()),
                    readSchema(definition.getOutputSchemaDocument())));
        }
        return result;
    }

    /**
     * 实体默认 UI 事件最终随 FORM/LIST 发布并以页面类型执行，因此按事件
     * 消费域选择操作；真正的实体变更等非 UI 绑定仍要求 ENTITY 上下文。
     */
    private Set<String> allowedOperationContexts(
            Owner owner,
            String bindingCode) {
        if (!"ENTITY".equals(owner.type())) {
            return Set.of(owner.type());
        }
        Set<String> pageContexts =
                UiEventBindingApplicability.contextsForEvent(bindingCode);
        return pageContexts.isEmpty()
                ? Set.of(owner.type()) : pageContexts;
    }

    private boolean isInvalidGlobalExtension(
            UiExtensionDefinition definition) {
        return "GLOBAL".equals(normalize(definition.getScopeType()))
                && "REGISTERED_PROVIDER".equals(
                        normalize(definition.getImplementationType()));
    }

    private boolean schemaMatches(
            String bindingCode,
            UiExtensionDefinition definition) {
        java.util.Map<String, Object> outputSchema =
                readSchema(definition.getOutputSchemaDocument());
        return switch (bindingCode) {
            case UiDataSourceUsages.FIELD_OPTIONS,
                    UiDataSourceUsages.SUBFORM_ROWS ->
                    "ARRAY".equals(schemaType(outputSchema));
            case UiDataSourceUsages.LIST_COLUMN ->
                    "OBJECT".equals(schemaType(outputSchema));
            case UiDataSourceUsages.LIST_QUERY ->
                    pageSchema(outputSchema);
            default -> true;
        };
    }

    private boolean pageSchema(
            java.util.Map<String, Object> schema) {
        if (!"OBJECT".equals(schemaType(schema))) {
            return false;
        }
        if (!(schema.get("properties")
                instanceof Map<?, ?> properties)
                || !(properties.get("records")
                instanceof Map<?, ?> recordsSchema)) {
            return false;
        }
        return "ARRAY".equals(schemaType(
                stringMap(recordsSchema)));
    }

    private String schemaType(
            java.util.Map<String, Object> schema) {
        return normalize(text(schema.get("type")));
    }

    private Owner resolveOwner(String ownerType, String ownerId) {
        String type = normalize(ownerType);
        if (!StringUtils.hasText(ownerId)) {
            throw new IllegalArgumentException("可用操作查询缺少 ownerId");
        }
        if ("FORM".equals(type)) {
            configurationAccessService.requireFormAccess(ownerId);
            EntityForm form = formMapper.selectById(ownerId);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            requireEntity(form.getEntityId());
            return new Owner(type, ownerId, form.getEntityId());
        }
        if ("LIST".equals(type)) {
            configurationAccessService.requireListAccess(ownerId);
            EntityListConfig list = listMapper.selectById(ownerId);
            if (list == null) {
                throw new IllegalArgumentException("列表不存在");
            }
            requireEntity(list.getEntityId());
            return new Owner(type, ownerId, list.getEntityId());
        }
        if ("ENTITY".equals(type)) {
            configurationAccessService.requireEntityAccess(ownerId);
            requireEntity(ownerId);
            return new Owner(type, ownerId, ownerId);
        }
        throw new IllegalArgumentException("ownerType 仅支持 FORM/LIST/ENTITY");
    }

    private EntityDefinition requireEntity(String entityId) {
        EntityDefinition entity = definitionMapper.selectById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在");
        }
        return entity;
    }

    private boolean scopeMatches(
            UiExtensionDefinition definition,
            Owner owner) {
        return switch (normalize(definition.getScopeType())) {
            case "GLOBAL" -> true;
            case "ENTITY" -> Objects.equals(
                    definition.getScopeId(),
                    owner.entityId());
            case "FORM" -> "FORM".equals(owner.type())
                    && Objects.equals(definition.getScopeId(), owner.id());
            case "LIST" -> "LIST".equals(owner.type())
                    && Objects.equals(definition.getScopeId(), owner.id());
            default -> false;
        };
    }

    private java.util.Map<String, Object> readSchema(String document) {
        if (!StringUtils.hasText(document)) {
            return java.util.Map.of();
        }
        try {
            return objectMapper.readValue(document,
                            new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(
                    "接口输出 Schema 损坏",
                    exception);
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private java.util.Map<String, Object> stringMap(
            java.util.Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result =
                new java.util.LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    /**
     * 服务端解析后的绑定所有者身份。
     *
     * @param type 所有者类型：FORM、LIST 或 ENTITY
     * @param id 所有者对象 ID
     * @param entityId 所有者所属实体 ID
     */
    private record Owner(
            String type,
            String id,
            String entityId) {
    }
}
