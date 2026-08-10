package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.response.UiAvailableOperation;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
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
 * 查询指定表单、列表或实体绑定位置可选择的接口操作。
 */
@Service
@RequiredArgsConstructor
public class UiAvailableOperationService {

    /** 只允许选择 READ 操作的绑定位置编码。 */
    private static final Set<String> READ_BINDINGS = Set.of(
            "FORM_INIT", "FIELD_OPTIONS", "FIELD_DEFAULT", "FIELD_COMPUTE",
            "SUBFORM_ROWS", "AFTER_LOAD", "LIST_QUERY", "LIST_COLUMN",
            "LIST_LOAD", "LIST_EXPORT", "DETAIL_LOAD", "FORM_OPEN",
            "SUBFORM_LOAD", "ENTITY_SELECTED");
    /** 只允许选择 WRITE 操作的绑定位置编码。 */
    private static final Set<String> WRITE_BINDINGS = Set.of(
            "BEFORE_SUBMIT", "DATA_CREATE", "DATA_UPDATE", "DATA_DELETE",
            "DATA_BATCH_DELETE", "FORM_SAVE", "SUBFORM_SAVE",
            "ENTITY_MUTATION_PREPARE");

    /** 接口服务定义查询入口。 */
    private final UiDataSourceDefinitionMapper sourceMapper;
    /** 实体定义查询入口，用于解析作用域所属实体。 */
    private final EntityDefinitionMapper definitionMapper;
    /** 表单配置查询入口，用于解析表单及其所属实体。 */
    private final EntityFormMapper formMapper;
    /** 列表配置查询入口，用于解析列表及其所属实体。 */
    private final EntityListConfigMapper listMapper;
    /** 操作 JSON 定义解析器。 */
    private final ObjectMapper objectMapper;
    /** 当前实体、表单或列表的配置访问校验服务。 */
    private final UiConfigurationAccessService configurationAccessService;

    public List<UiAvailableOperation> available(
            String ownerType,
            String ownerId,
            String bindingCode) {
        Owner owner = resolveOwner(ownerType, ownerId);
        String normalizedBinding = normalize(bindingCode);
        List<UiDataSourceDefinition> definitions = sourceMapper.selectList(
                new LambdaQueryWrapper<UiDataSourceDefinition>()
                        .eq(UiDataSourceDefinition::getEnabled, true)
                        .eq(UiDataSourceDefinition::getDeleted, 0)
                        .orderByAsc(UiDataSourceDefinition::getSourceCode));
        List<UiAvailableOperation> result = new ArrayList<>();
        for (UiDataSourceDefinition definition : definitions) {
            if (isInvalidGlobalExtension(definition)
                    || !scopeMatches(definition, owner)) {
                continue;
            }
            for (Map<String, Object> operation : operations(definition)) {
                String contextType = normalize(text(operation.get("contextType")));
                String kind = normalize(text(operation.getOrDefault("kind", "READ")));
                if (!owner.type().equals(contextType)) {
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
                if (!schemaMatches(normalizedBinding, operation)) {
                    continue;
                }
                result.add(new UiAvailableOperation(
                        definition.getId(),
                        definition.getSourceCode(),
                        definition.getSourceName(),
                        definition.getSourceType(),
                        definition.getScopeType(),
                        definition.getScopeId(),
                        text(operation.get("code")),
                        text(operation.get("name")),
                        kind,
                        contextType));
            }
        }
        return result;
    }

    private boolean isInvalidGlobalExtension(
            UiDataSourceDefinition definition) {
        return "GLOBAL".equals(normalize(definition.getScopeType()))
                && Set.of(
                        "REGISTERED_PROVIDER",
                        "INTEGRATION_CONNECTOR")
                .contains(normalize(definition.getSourceType()));
    }

    private boolean schemaMatches(
            String bindingCode,
            Map<String, Object> operation) {
        Map<String, Object> outputSchema =
                operation.get("outputSchema") instanceof Map<?, ?> map
                        ? stringMap(map)
                        : Map.of();
        return switch (bindingCode) {
            case "FIELD_OPTIONS", "SUBFORM_ROWS" ->
                    "ARRAY".equals(schemaType(outputSchema));
            case "LIST_COLUMN" ->
                    "OBJECT".equals(schemaType(outputSchema));
            case "LIST_QUERY" ->
                    pageSchema(outputSchema);
            default -> true;
        };
    }

    private boolean pageSchema(
            Map<String, Object> schema) {
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
            Map<String, Object> schema) {
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
            UiDataSourceDefinition definition,
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

    private List<Map<String, Object>> operations(
            UiDataSourceDefinition definition) {
        if (!StringUtils.hasText(definition.getOperationsDocument())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    definition.getOperationsDocument(),
                    new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "接口服务操作定义损坏: "
                            + definition.getSourceCode(),
                    exception);
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> stringMap(
            Map<?, ?> source) {
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
