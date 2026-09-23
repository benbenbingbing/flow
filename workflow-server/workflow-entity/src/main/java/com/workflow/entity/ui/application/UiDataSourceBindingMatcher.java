package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Locates exact interface-extension bindings in draft and release snapshots.
 *
 * <p>新配置只匹配 extensionId；历史不可变快照仍可用
 * serviceId/dataSourceId + operationCode 精确匹配。</p>
 */
@Component
@RequiredArgsConstructor
public class UiDataSourceBindingMatcher {

    private final UiEventBindingMapper eventBindingMapper;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    /**
     * 查询草稿事件；查询结果供调用方展示或继续处理。
     *
     * @param configType 配置类型标识，决定后续草稿事件采用的处理分支
     * @param configId 配置ID，后续用于查询草稿事件时定位或关联目标
     * @param entityId 实体ID，后续用于查询草稿事件时定位或关联目标
     * @param usage 使用场景，作为 {@code findEventBinding} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续草稿事件采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询草稿事件时定位或关联目标
     * @param operationCode 操作编码，后续用于查询草稿事件时定位或关联目标
     * @return 查询后的草稿事件文本，供调用方比较或展示
     */
    public String findDraftEvent(
            String configType,
            String configId,
            String entityId,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode) {
        List<Map<String, Object>> bindings =
                objectMapper.convertValue(
                        eventBindingMapper.findForSnapshot(
                                configType,
                                configId,
                                entityId),
                        new TypeReference<>() {});
        return findEventBinding(
                bindings,
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.draft.eventBindings",
                null,
                null);
    }

    /**
     * 查询已发布；查询结果供调用方展示或继续处理。
     *
     * @param configType 配置类型标识，决定后续已发布采用的处理分支
     * @param snapshot 快照，供本方法查询已发布时使用
     * @param usage 使用场景，供本方法查询已发布时使用
     * @param targetType 目标类型标识，决定后续已发布采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询已发布时定位或关联目标
     * @param operationCode 操作编码，后续用于查询已发布时定位或关联目标
     * @return 查询后的已发布文本，供调用方比较或展示
     */
    public String findPublished(
            String configType,
            Map<String, Object> snapshot,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode) {
        return findPublished(
                configType,
                snapshot,
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                null,
                null);
    }

    /**
     * 按事件步骤的可信来源所有者精确查找发布绑定。
     * 所有者为空时保持通用接口调用的既有匹配行为。
     *
     * @param configType 配置类型标识，决定后续已发布采用的处理分支
     * @param snapshot 快照，作为 {@code findEventBinding} 的输入影响后续处理
     * @param usage 使用场景，作为 {@code findForm} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续已发布采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询已发布时定位或关联目标
     * @param operationCode 操作编码，后续用于查询已发布时定位或关联目标
     * @param bindingOwnerType 绑定归属方类型标识，决定后续已发布采用的处理分支
     * @param bindingOwnerId 绑定归属方ID，后续用于查询已发布时定位或关联目标
     * @return 查询后的已发布文本，供调用方比较或展示
     */
    public String findPublished(
            String configType,
            Map<String, Object> snapshot,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String bindingOwnerType,
            String bindingOwnerId) {
        String eventPath = findEventBinding(
                mapList(snapshot.get("eventBindings")),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.release.eventBindings",
                bindingOwnerType,
                bindingOwnerId);
        if (StringUtils.hasText(eventPath)) {
            return eventPath;
        }
        String compositionPath = findViewComposition(
                mapList(snapshot.get("viewCompositions")),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.release.viewCompositions");
        if (StringUtils.hasText(compositionPath)) {
            return compositionPath;
        }
        if ("FORM".equals(configType)) {
            List<Map<String, Object>> owners = new ArrayList<>();
            addMap(owners, snapshot.get("form"));
            addMaps(owners, snapshot.get("nodes"));
            addMaps(owners, snapshot.get("legacyFields"));
            return findForm(
                    owners,
                    usage,
                    targetType,
                    targetKey,
                    sourceId,
                    operationCode,
                    "$.release.form");
        }
        Map<String, Object> list = stringMap(snapshot.get("list"));
        return findList(
                list,
                mapList(list.get("fields")),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.release.list");
    }

    /**
     * 查找关联内容中特殊处理的精确接口操作绑定。
     *
     * <p>关联内容不是表单字段或列表列，必须同时匹配固定 usage、
     * COMPOSITION 目标类型和 compositionKey，避免同一页面其它接口服务
     * 被借用执行。</p>
     *
     * @param compositions {@code compositions}，供本方法查询视图组合时使用
     * @param usage 使用场景，作为 {@code normalize} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续视图组合采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询视图组合时定位或关联目标
     * @param operationCode 操作编码，后续用于查询视图组合时定位或关联目标
     * @param basePath 基础路径，供本方法查询视图组合时使用
     * @return 查询后的视图组合文本，供调用方比较或展示
     */
    private String findViewComposition(
            List<Map<String, Object>> compositions,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String basePath) {
        String normalizedUsage = normalize(usage);
        boolean resolve = UiDataSourceUsages.RELATED_CONTENT_RESOLVE.equals(
                normalizedUsage);
        boolean action = UiDataSourceUsages.RELATED_CONTENT_ACTION.equals(
                normalizedUsage);
        if ((!resolve && !action) || !StringUtils.hasText(targetKey)
                || resolve && !"COMPOSITION".equals(normalize(targetType))
                || action && !"COMPOSITION_ACTION".equals(
                normalize(targetType))) {
            return null;
        }
        String compositionKey = resolve ? targetKey.trim()
                : beforeSeparator(targetKey, "::");
        String actionKey = action ? afterSeparator(targetKey, "::") : null;
        if (!StringUtils.hasText(compositionKey)
                || action && !StringUtils.hasText(actionKey)) {
            return null;
        }
        for (int index = 0; index < compositions.size(); index++) {
            Map<String, Object> composition = compositions.get(index);
            if (!Objects.equals(
                    compositionKey,
                    text(composition.get("compositionKey")))) {
                continue;
            }
            Map<String, Object> config = stringMap(
                    composition.get("config"));
            Map<String, Object> special = stringMap(
                    config.get("specialHandling"));
            if (resolve && matchesBinding(
                    stringMap(special.get("interfaceService")),
                    sourceId, operationCode)) {
                return basePath
                        + "[" + index + "]"
                        + ".config.specialHandling.interfaceService";
            }
            if (action) {
                List<Map<String, Object>> services = mapList(
                        special.get("actionServices"));
                for (int actionIndex = 0;
                        actionIndex < services.size(); actionIndex++) {
                    Map<String, Object> service = services.get(actionIndex);
                    if (Objects.equals(
                            normalize(actionKey),
                            normalize(text(service.get("actionKey"))))
                            && matchesBinding(
                            service, sourceId, operationCode)) {
                        return basePath + "[" + index + "]"
                                + ".config.specialHandling.actionServices["
                                + actionIndex + "]";
                    }
                }
            }
        }
        return null;
    }

    /**
     * 生成之前{@code separator}文本，供后续匹配或展示。
     *
     * @param value 待处理之前{@code separator}的原始输入，结果供调用方继续使用
     * @param separator {@code separator}，作为 {@code value.indexOf} 的输入影响后续处理
     * @return 处理后的之前{@code separator}文本，供调用方比较或展示
     */
    private String beforeSeparator(String value, String separator) {
        int index = value == null ? -1 : value.indexOf(separator);
        return index <= 0 ? null : value.substring(0, index).trim();
    }

    /**
     * 生成之后{@code separator}文本，供后续匹配或展示。
     *
     * @param value 待处理之后{@code separator}的原始输入，结果供调用方继续使用
     * @param separator {@code separator}，作为 {@code value.indexOf} 的输入影响后续处理
     * @return 处理后的之后{@code separator}文本，供调用方比较或展示
     */
    private String afterSeparator(String value, String separator) {
        int index = value == null ? -1 : value.indexOf(separator);
        return index < 0 || index + separator.length() >= value.length()
                ? null : value.substring(index + separator.length()).trim();
    }

    /**
     * 查询表单；查询结果供调用方展示或继续处理。
     *
     * @param owners {@code owners}，供本方法查询表单时使用
     * @param usage 使用场景，作为 {@code findOwnerBinding} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续表单采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询表单时定位或关联目标
     * @param operationCode 操作编码，后续用于查询表单时定位或关联目标
     * @param basePath 基础路径，供本方法查询表单时使用
     * @return 查询后的表单文本，供调用方比较或展示
     */
    public String findForm(
            List<Map<String, Object>> owners,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String basePath) {
        for (int index = 0; index < owners.size(); index++) {
            Map<String, Object> owner = owners.get(index);
            String ownerPath = basePath + "[" + index + "]";
            if (!formOwnerMatches(
                    owner,
                    targetType,
                    targetKey)) {
                continue;
            }
            String bindingPath =
                    findOwnerBinding(
                            owner,
                            usage,
                            sourceId,
                            operationCode,
                            ownerPath);
            if (StringUtils.hasText(bindingPath)) {
                return bindingPath;
            }
        }
        return null;
    }

    /**
     * 查询界面数据来源绑定匹配器列表；查询结果供调用方展示或继续处理。
     *
     * @param list 列表，作为 {@code findOwnerBinding} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param usage 使用场景，作为 {@code findOwnerBinding} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续界面数据来源绑定匹配器列表采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询界面数据来源绑定匹配器列表时定位或关联目标
     * @param operationCode 操作编码，后续用于查询界面数据来源绑定匹配器列表时定位或关联目标
     * @param basePath 基础路径，作为 {@code findOwnerBinding} 的输入影响后续处理
     * @return 查询后的界面数据来源绑定匹配器列表文本，供调用方比较或展示
     */
    public String findList(
            Map<String, Object> list,
            List<Map<String, Object>> fields,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String basePath) {
        if ("OWNER".equals(normalize(targetType))) {
            if (UiDataSourceUsages.LIST_QUERY.equals(
                    normalize(usage))
                    && matchesDirectReference(
                            list,
                            sourceId,
                            operationCode,
                            "queryInterfaceExtensionId",
                            "queryDataSourceId",
                            "queryOperationCode")) {
                return basePath + ".queryInterfaceExtensionId";
            }
            String ownerBinding = findOwnerBinding(
                    list,
                    usage,
                    sourceId,
                    operationCode,
                    basePath);
            if (StringUtils.hasText(ownerBinding)) {
                return ownerBinding;
            }
        }
        for (int index = 0; index < fields.size(); index++) {
            Map<String, Object> field = fields.get(index);
            if (!listFieldMatches(
                    field,
                    targetType,
                    targetKey)) {
                continue;
            }
            if (UiDataSourceUsages.LIST_COLUMN.equals(usage)
                    && matchesDirectReference(
                            field,
                            sourceId,
                            operationCode,
                            "interfaceExtensionId",
                            "dataSourceId",
                            "dataSourceOperationCode")) {
                return basePath
                        + ".fields[" + index + "].interfaceExtensionId";
            }
            String bindingPath = findOwnerBinding(
                    field,
                    usage,
                    sourceId,
                    operationCode,
                    basePath + ".fields[" + index + "]");
            if (StringUtils.hasText(bindingPath)) {
                return bindingPath;
            }
        }
        return null;
    }

    /**
     * 查询归属方绑定；查询结果供调用方展示或继续处理。
     *
     * @param owner 归属方，作为 {@code parseObject} 的输入影响后续处理
     * @param usage 使用场景，作为 {@code findConfiguredBinding} 的输入影响后续处理
     * @param sourceId 来源ID，后续用于查询归属方绑定时定位或关联目标
     * @param operationCode 操作编码，后续用于查询归属方绑定时定位或关联目标
     * @param ownerPath 归属方路径，作为 {@code findConfiguredBinding} 的输入影响后续处理
     * @return 查询后的归属方绑定文本，供调用方比较或展示
     */
    private String findOwnerBinding(
            Map<String, Object> owner,
            String usage,
            String sourceId,
            String operationCode,
            String ownerPath) {
        Map<String, Object> bindings = parseObject(
                owner.get("dataSourceBindings") != null
                        ? owner.get("dataSourceBindings")
                        : owner.get("dataSourceBindingsDocument"),
                "数据源绑定");
        return findConfiguredBinding(
                bindings,
                usage,
                sourceId,
                operationCode,
                ownerPath + ".dataSourceBindings");
    }

    /**
     * 查询已配置绑定；查询结果供调用方展示或继续处理。
     *
     * @param bindings 绑定集合，作为 {@code usage.equals} 的输入影响后续处理
     * @param usage 使用场景，作为 {@code usage.equals} 的输入影响后续处理
     * @param sourceId 来源ID，后续用于查询已配置绑定时定位或关联目标
     * @param operationCode 操作编码，后续用于查询已配置绑定时定位或关联目标
     * @param path 路径，供本方法查询已配置绑定时使用
     * @return 查询后的已配置绑定文本，供调用方比较或展示
     */
    private String findConfiguredBinding(
            Map<String, Object> bindings,
            String usage,
            String sourceId,
            String operationCode,
            String path) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        Object configured = null;
        String matchedKey = usage;
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            if (usage.equals(normalize(entry.getKey()))) {
                configured = entry.getValue();
                matchedKey = entry.getKey();
                break;
            }
        }
        if (configured == null) {
            return usage.equals(normalize(
                    text(bindings.get("usage"))))
                    && matchesBinding(
                            bindings,
                            sourceId,
                            operationCode)
                    ? path : null;
        }
        return containsOperation(
                configured,
                sourceId,
                operationCode)
                ? path + "." + matchedKey
                : null;
    }

    /**
     * 查询事件绑定；查询结果供调用方展示或继续处理。
     *
     * @param bindings 绑定集合，供本方法查询事件绑定时使用
     * @param usage 使用场景，作为 {@code UiDataSourceUsages.LIST_QUERY.equals} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续事件绑定采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询事件绑定时定位或关联目标
     * @param operationCode 操作编码，后续用于查询事件绑定时定位或关联目标
     * @param path 路径，供本方法查询事件绑定时使用
     * @param bindingOwnerType 绑定归属方类型标识，决定后续事件绑定采用的处理分支
     * @param bindingOwnerId 绑定归属方ID，后续用于查询事件绑定时定位或关联目标
     * @return 查询后的事件绑定文本，供调用方比较或展示
     */
    private String findEventBinding(
            List<Map<String, Object>> bindings,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String path,
            String bindingOwnerType,
            String bindingOwnerId) {
        for (int index = 0; index < bindings.size(); index++) {
            Map<String, Object> binding = bindings.get(index);
            if ((StringUtils.hasText(bindingOwnerType)
                    || StringUtils.hasText(bindingOwnerId))
                    && (!normalize(bindingOwnerType).equals(normalize(
                    text(binding.get("ownerType"))))
                    || !Objects.equals(
                    text(bindingOwnerId),
                    text(binding.get("ownerId"))))) {
                continue;
            }
            boolean legacyListQuery = UiDataSourceUsages.LIST_QUERY.equals(usage)
                    && UiDataSourceUsages.LIST_LOAD.equals(normalize(text(binding.get("eventCode"))));
            if (!legacyListQuery && !usage.equals(normalize(text(binding.get("eventCode"))))) {
                continue;
            }
            if (!eventTargetMatches(
                    binding,
                    targetType,
                    targetKey)) {
                continue;
            }
            Object steps = binding.get("steps");
            if (steps == null) {
                steps = parseArray(
                        binding.get("stepsDocument"),
                        "UI事件绑定步骤");
            }
            // 仅迁移标记的替代步骤允许使用旧 LIST_QUERY 契约，不能借用任意 LIST_LOAD 接口。
            if (legacyListQuery) {
                steps = mapList(steps).stream().filter(step -> Boolean.TRUE.equals(step.get("legacyListQuery"))
                        && "REPLACE".equals(normalize(text(step.get("strategy"))))).toList();
            }
            if (containsOperation(
                    steps,
                    sourceId,
                    operationCode)) {
                return path + "[" + index + "].steps";
            }
        }
        return null;
    }

    /**
     * 判断事件目标匹配条件是否成立，供调用方选择后续分支。
     *
     * @param binding 绑定，作为 {@code equals} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续事件目标匹配采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @return 事件目标匹配条件成立时为 true，否则为 false
     */
    private boolean eventTargetMatches(
            Map<String, Object> binding,
            String targetType,
            String targetKey) {
        return normalize(targetType).equals(normalize(
                text(binding.getOrDefault("targetType", "OWNER"))))
                && normalizedTargetKey(targetType, targetKey).equals(
                        normalizedTargetKey(
                                text(binding.getOrDefault(
                                        "targetType",
                                        "OWNER")),
                                text(binding.get("targetKey"))));
    }

    /**
     * 判断表单归属方匹配条件是否成立，供调用方选择后续分支。
     *
     * @param owner 归属方，作为 {@code parseObject} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续表单归属方匹配采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @return 表单归属方匹配条件成立时为 true，否则为 false
     */
    private boolean formOwnerMatches(
            Map<String, Object> owner,
            String targetType,
            String targetKey) {
        String type = normalize(targetType);
        if ("OWNER".equals(type)) {
            return StringUtils.hasText(text(owner.get("formKey")))
                    && !StringUtils.hasText(text(owner.get("nodeKey")));
        }
        if (!Set.of("FIELD", "NODE").contains(type)) {
            return false;
        }
        Map<String, Object> properties = parseObject(
                owner.get("propsDocument"),
                "表单节点属性");
        String ownerKey = "NODE".equals(type)
                ? firstText(
                        owner.get("nodeKey"),
                        owner.get("fieldCode"),
                        properties.get("fieldCode"))
                : firstText(
                        owner.get("fieldCode"),
                        properties.get("fieldCode"),
                        owner.get("nodeKey"));
        return Objects.equals(
                normalizedTargetKey(type, targetKey),
                normalizedTargetKey(type, ownerKey));
    }

    /**
     * 列出字段匹配；查询结果供调用方展示或继续处理。
     *
     * @param field 字段，作为 {@code text} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续字段匹配采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @return 字段匹配条件成立时为 true，否则为 false
     */
    private boolean listFieldMatches(
            Map<String, Object> field,
            String targetType,
            String targetKey) {
        String type = normalize(targetType);
        return Set.of("COLUMN", "FIELD").contains(type)
                && Objects.equals(
                        normalizedTargetKey(type, targetKey),
                        normalizedTargetKey(
                                type,
                                text(field.get("fieldCode"))));
    }

    /**
     * 生成规范化目标键文本，供后续匹配或展示。
     *
     * @param targetType 目标类型标识，决定后续规范化目标键采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @return 处理后的规范化目标键文本，供调用方比较或展示
     */
    private String normalizedTargetKey(
            String targetType,
            String targetKey) {
        return "OWNER".equals(normalize(targetType))
                ? ""
                : StringUtils.hasText(targetKey)
                        ? targetKey.trim()
                        : "";
    }

    /**
     * 判断是否包含操作；判断结果决定调用方的后续分支。
     *
     * @param configured 已配置，供本方法判断是否包含操作时使用
     * @param sourceId 来源ID，后续用于判断是否包含操作时定位或关联目标
     * @param operationCode 操作编码，后续用于判断是否包含操作时定位或关联目标
     * @return 操作条件成立时为 true，否则为 false
     */
    private boolean containsOperation(
            Object configured,
            String sourceId,
            String operationCode) {
        if (configured instanceof Map<?, ?> map) {
            return matchesBinding(map, sourceId, operationCode)
                    || map.values().stream()
                            .anyMatch(item ->
                                    containsOperation(
                                            item,
                                            sourceId,
                                            operationCode));
        }
        return configured instanceof List<?> list
                && list.stream().anyMatch(item ->
                        containsOperation(
                                item,
                                sourceId,
                                operationCode));
    }

    /**
     * 判断是否匹配绑定；判断结果决定调用方的后续分支。
     *
     * @param binding 绑定，作为 {@code firstText} 的输入影响后续处理
     * @param sourceId 来源ID，后续用于判断是否匹配绑定时定位或关联目标
     * @param operationCode 操作编码，后续用于判断是否匹配绑定时定位或关联目标
     * @return 绑定条件成立时为 true，否则为 false
     */
    private boolean matchesBinding(
            Map<?, ?> binding,
            String sourceId,
            String operationCode) {
        String extensionId = firstText(
                binding.get("extensionId"),
                binding.get("interfaceExtensionId"),
                binding.get("queryInterfaceExtensionId"));
        if (StringUtils.hasText(extensionId)) {
            return Objects.equals(sourceId, extensionId.trim());
        }
        return Objects.equals(sourceId, legacyReferenceId(binding))
                && Objects.equals(
                        operationCode,
                        firstText(
                                binding.get("operationCode"),
                                binding.get("dataSourceOperationCode"),
                                binding.get("queryOperationCode")));
    }

    /**
     * 判断是否匹配{@code direct}引用；判断结果决定调用方的后续分支。
     *
     * @param binding 绑定，作为 {@code text} 的输入影响后续处理
     * @param sourceId 来源ID，后续用于判断是否匹配{@code direct}引用时定位或关联目标
     * @param operationCode 操作编码，后续用于判断是否匹配{@code direct}引用时定位或关联目标
     * @param extensionKey 扩展键，后续用于授权校验、关联或幂等去重
     * @param legacyIdKey 旧版ID键，后续用于授权校验、关联或幂等去重
     * @param legacyOperationKey 旧版操作键，后续用于授权校验、关联或幂等去重
     * @return {@code direct}引用条件成立时为 true，否则为 false
     */
    private boolean matchesDirectReference(
            Map<?, ?> binding,
            String sourceId,
            String operationCode,
            String extensionKey,
            String legacyIdKey,
            String legacyOperationKey) {
        String extensionId = text(binding.get(extensionKey));
        if (StringUtils.hasText(extensionId)) {
            return Objects.equals(sourceId, extensionId.trim());
        }
        return Objects.equals(sourceId, text(binding.get(legacyIdKey)))
                && Objects.equals(
                        operationCode,
                        text(binding.get(legacyOperationKey)));
    }

    /**
     * 生成旧版引用ID文本，供后续匹配或展示。
     *
     * @param binding 绑定，作为 {@code firstText} 的输入影响后续处理
     * @return 处理后的旧版引用ID文本，供调用方比较或展示
     */
    private String legacyReferenceId(Map<?, ?> binding) {
        String value = firstText(
                binding.get("serviceId"),
                binding.get("dataSourceId"),
                binding.get("queryDataSourceId"));
        return StringUtils.hasText(value)
                ? value.trim()
                : null;
    }

    /**
     * 解析对象；输出作为后续校验或处理的输入。
     *
     * @param value 待解析对象的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于解析对象时匹配或展示
     * @return 对象键值结果，供调用方继续处理
     */
    private Map<String, Object> parseObject(
            Object value,
            String label) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        return value instanceof String document
                && StringUtils.hasText(document)
                ? codec.readObject(document, label)
                : Map.of();
    }

    /**
     * 解析数组；输出作为后续校验或处理的输入。
     *
     * @param value 待解析数组的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于解析数组时匹配或展示
     * @return 界面数据来源绑定匹配器集合，供调用方遍历或展示
     */
    private List<Object> parseArray(
            Object value,
            String label) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value instanceof String document
                && StringUtils.hasText(document)
                ? codec.readArray(document, label)
                : List.of();
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param value 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 界面数据来源绑定匹配器集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        addMaps(result, list);
        return result;
    }

    /**
     * 添加映射；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加映射时使用
     * @param value 待添加映射的原始输入，结果供调用方继续使用
     */
    private void addMap(
            List<Map<String, Object>> target,
            Object value) {
        Map<String, Object> map = stringMap(value);
        if (!map.isEmpty()) {
            target.add(map);
        }
    }

    /**
     * 添加{@code maps}；结果供后续流程传递或持久化。
     *
     * @param target 目标，作为 {@code list.forEach} 的输入影响后续处理
     * @param value 待添加{@code maps}的原始输入，结果供调用方继续使用
     */
    private void addMaps(
            List<Map<String, Object>> target,
            Object value) {
        if (value instanceof List<?> list) {
            list.forEach(item -> addMap(target, item));
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
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面数据来源绑定匹配器的原始输入，结果供调用方继续使用
     * @return 规范化后的界面数据来源绑定匹配器文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }
}
