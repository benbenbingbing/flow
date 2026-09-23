package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.SystemEntityService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 单选实体字段事件的权威选择数据加载器。
 */
@Service
@RequiredArgsConstructor
public class EntitySelectionRuntimeService {

    private static final Set<String> SYSTEM_TYPES =
            Set.of("USER", "DEPT", "ROLE", "GROUP");

    private final EntityDataDynamicService entityDataService;
    private final SystemEntityService systemEntityService;
    private final SystemEntityReadService systemEntityReadService;
    private final EntityDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据表单快照中的引用配置，补查单选实体事件使用的权威数据。
     *
     * <p>仅在配置了事件步骤时加载；清空选择返回 null，多选沿用原始选择。
     * 查询权限或记录不可用的异常直接向上传递，不能退回客户端提交的数据。</p>
     *
     * @param request 事件请求，仅使用其中的选择 ID 定位权威记录
     * @param chain 已解析的事件链，包含可信的表单配置快照
     * @return 供事件映射读取的选择数据，保留 selectionData 扩展上下文
     */
    public Object resolve(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain) {
        if (request == null
                || !UiDataSourceUsages.ENTITY_SELECTED.equals(normalize(
                        request.getEventCode()))
                || !"FORM".equals(normalize(request.getConfigType()))
                || !"FIELD".equals(normalize(request.getTargetType()))
                || chain == null
                || chain.steps().isEmpty()) {
            return request == null ? null : request.getSelection();
        }
        ReferenceConfig reference = referenceConfig(
                chain.snapshot(),
                request.getTargetKey());
        if (reference == null || reference.multiple()) {
            return request.getSelection();
        }
        String selectedId = selectedId(request.getSelection());
        if (!StringUtils.hasText(selectedId)) {
            return null;
        }
        Map<String, Object> authoritative;
        if ("CUSTOM".equals(reference.entityType())) {
            String entityCode = reference.entityCode();
            if (!StringUtils.hasText(entityCode)
                    && StringUtils.hasText(reference.entityId())) {
                EntityDefinition definition =
                        definitionMapper.selectById(reference.entityId());
                entityCode = definition == null
                        ? null : definition.getEntityCode();
            }
            if (!StringUtils.hasText(entityCode)) {
                throw new IllegalArgumentException(
                        "单选实体字段未配置有效的关联实体");
            }
            // CUSTOM 表示通过实体目录引用，也可能指向 sys_user 等系统实体。
            // 与选择器使用同一只读服务，保留权限和字段过滤，并兼容已发布的引用配置。
            EntityDataDTO detail = systemEntityReadService.isSystemEntity(entityCode)
                    ? systemEntityReadService.findById(entityCode, selectedId)
                    : entityDataService.findAccessibleById(
                            entityCode,
                            selectedId,
                            reference.listKey());
            authoritative = objectMapper.convertValue(
                    detail,
                    new TypeReference<Map<String, Object>>() {});
            authoritative.put("entityType", "CUSTOM");
        } else if (SYSTEM_TYPES.contains(reference.entityType())) {
            Map<String, Object> detail =
                    systemEntityService.selectById(
                            reference.entityType(),
                            selectedId);
            if (detail == null) {
                throw new IllegalArgumentException(
                        "选择的系统实体数据不存在或已失效");
            }
            authoritative = new LinkedHashMap<>(detail);
        } else {
            throw new IllegalArgumentException(
                    "不支持的引用实体类型: "
                            + reference.entityType());
        }
        if (request.getSelection() instanceof Map<?, ?> clientSelection
                && clientSelection.containsKey("selectionData")) {
            authoritative.put(
                    "selectionData",
                    clientSelection.get("selectionData"));
        }
        return authoritative;
    }

    /**
     * 处理引用配置，并将结果传给后续步骤。
     *
     * @param snapshot 快照，供本方法处理引用配置时使用
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @return 处理后的引用配置结果，供调用方继续处理
     */
    private ReferenceConfig referenceConfig(
            Map<String, Object> snapshot,
            String targetKey) {
        if (snapshot == null || !StringUtils.hasText(targetKey)) {
            return null;
        }
        for (Map<String, Object> node :
                mapList(snapshot.get("nodes"))) {
            Map<String, Object> props = objectMap(
                    node.get("propsDocument"));
            String fieldCode = firstText(
                    props.get("fieldCode"),
                    node.get("nodeKey"));
            if (targetKey.equals(fieldCode)) {
                return fromField(props);
            }
        }
        for (Map<String, Object> field :
                mapList(snapshot.get("legacyFields"))) {
            if (targetKey.equals(text(field.get("fieldCode")))) {
                return fromField(field);
            }
        }
        return null;
    }

    /**
     * 处理起始字段，并将结果传给后续步骤。
     *
     * @param field 字段，作为 {@code objectMap} 的输入影响后续处理
     * @return 处理后的起始字段结果，供调用方继续处理
     */
    private ReferenceConfig fromField(
            Map<String, Object> field) {
        Map<String, Object> componentProps =
                objectMap(field.get("componentProps"));
        Map<String, Object> refConfig =
                objectMap(componentProps.get("refConfig"));
        String fieldType = normalize(firstText(
                field.get("fieldType"),
                field.get("componentType")));
        String componentType =
                normalize(text(field.get("componentType")));
        boolean multiple =
                "MULTI_REFERENCE".equals(fieldType)
                        || "MULTI_REFERENCE".equals(componentType);
        boolean reference = multiple
                || Set.of(
                        "REFERENCE", "USER", "DEPT",
                        "ROLE", "GROUP")
                .contains(fieldType)
                || "REFERENCE".equals(componentType)
                || !refConfig.isEmpty()
                || field.containsKey("refEntityId")
                || field.containsKey("refEntityType");
        if (!reference) {
            return null;
        }
        String entityType = normalize(firstText(
                refConfig.get("refEntityType"),
                field.get("refEntityType"),
                SYSTEM_TYPES.contains(fieldType)
                        ? fieldType : "CUSTOM"));
        return new ReferenceConfig(
                entityType,
                firstText(
                        refConfig.get("refEntityId"),
                        field.get("refEntityId")),
                firstText(
                        refConfig.get("entityCode"),
                        field.get("refEntityCode")),
                firstText(
                        refConfig.get("listKey"),
                        field.get("refListKey")),
                multiple);
    }

    /**
     * 生成已选择ID文本，供后续匹配或展示。
     *
     * @param selection 选择，作为 {@code text} 的输入影响后续处理
     * @return 处理后的已选择ID文本，供调用方比较或展示
     */
    private String selectedId(Object selection) {
        if (selection instanceof Map<?, ?> map) {
            return firstText(
                    map.get("id"),
                    map.get("value"));
        }
        if (selection instanceof List<?>) {
            return null;
        }
        return text(selection);
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 实体选择集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> stringMap((Map<?, ?>) item))
                .toList();
    }

    /**
     * 整理对象映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理对象映射的原始输入，结果供调用方继续使用
     * @return 对象映射键值结果，供调用方继续处理
     */
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (value instanceof String document
                && StringUtils.hasText(document)) {
            try {
                return objectMapper.readValue(
                        document,
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param source 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化实体选择运行时的原始输入，结果供调用方继续使用
     * @return 规范化后的实体选择运行时文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
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
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 封装引用配置的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityType 实体类型标识，决定后续引用配置采用的处理分支
     * @param entityId 实体ID，后续用于处理引用配置时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param multiple {@code multiple}，保存在对象中供后续校验、查询或展示
     */
    private record ReferenceConfig(
            String entityType,
            String entityId,
            String entityCode,
            String listKey,
            boolean multiple) {
    }
}
