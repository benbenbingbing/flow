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
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
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

    /**
     * 整理可用数据，供调用方遍历或继续处理。
     *
     * @param ownerType 归属方类型标识，决定后续可用采用的处理分支
     * @param ownerId 归属方ID，后续用于处理可用时定位或关联目标
     * @param bindingCode 绑定编码，后续用于处理可用时定位或关联目标
     * @return 界面可用接口集合，供调用方遍历或展示
     */
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
     *
     * @param owner 归属方，供本方法处理允许操作{@code contexts}时使用
     * @param bindingCode 绑定编码，后续用于处理允许操作{@code contexts}时定位或关联目标
     * @return 界面可用接口集合，供调用方遍历或展示
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

    /**
     * 判断是否无效全局扩展；判断结果决定调用方的后续分支。
     *
     * @param definition 定义，作为 {@code equals} 的输入影响后续处理
     * @return 无效全局扩展条件成立时为 true，否则为 false
     */
    private boolean isInvalidGlobalExtension(
            UiExtensionDefinition definition) {
        return "GLOBAL".equals(normalize(definition.getScopeType()))
                && "REGISTERED_PROVIDER".equals(
                        normalize(definition.getImplementationType()));
    }

    /**
     * 判断结构匹配条件是否成立，供调用方选择后续分支。
     *
     * @param bindingCode 绑定编码，后续用于处理结构匹配时定位或关联目标
     * @param definition 定义，作为 {@code readSchema} 的输入影响后续处理
     * @return 结构匹配条件成立时为 true，否则为 false
     */
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

    /**
     * 分页查询结构；查询结果供调用方展示或继续处理。
     *
     * @param schema 结构，供本方法分页查询结构时使用
     * @return 结构条件成立时为 true，否则为 false
     */
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

    /**
     * 生成结构类型文本，供后续匹配或展示。
     *
     * @param schema 结构，作为 {@code normalize} 的输入影响后续处理
     * @return 处理后的结构类型文本，供调用方比较或展示
     */
    private String schemaType(
            java.util.Map<String, Object> schema) {
        return normalize(text(schema.get("type")));
    }

    /**
     * 解析归属方；输出作为后续校验或处理的输入。
     *
     * @param ownerType 归属方类型标识，决定后续归属方采用的处理分支
     * @param ownerId 归属方ID，后续用于解析归属方时定位或关联目标
     * @return 解析后的归属方结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验并获取实体；不满足约束时阻止后续处理。
     *
     * @param entityId 实体ID，后续用于校验并获取实体时定位或关联目标
     * @return 校验并获取后的实体结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityDefinition requireEntity(String entityId) {
        EntityDefinition entity = definitionMapper.selectById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在");
        }
        return entity;
    }

    /**
     * 判断作用域匹配条件是否成立，供调用方选择后续分支。
     *
     * @param definition 定义，供本方法处理作用域匹配时使用
     * @param owner 归属方，作为 {@code equals} 的输入影响后续处理
     * @return 作用域匹配条件成立时为 true，否则为 false
     */
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

    /**
     * 读取结构；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 读取后的结构结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param source 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 处理后的字符串映射结果，供调用方继续处理
     */
    private java.util.Map<String, Object> stringMap(
            java.util.Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result =
                new java.util.LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面可用接口的原始输入，结果供调用方继续使用
     * @return 规范化后的界面可用接口文本，供调用方比较或展示
     */
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
