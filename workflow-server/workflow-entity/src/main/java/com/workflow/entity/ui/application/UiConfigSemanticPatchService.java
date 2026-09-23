package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.api.response.UiConfigHotfixRiskItemDTO;
import com.workflow.entity.ui.api.model.UiConfigSemanticPatchOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UI 配置稳定 ID 语义补丁构建、风险分级和跨版本应用服务。
 */
@Service
@RequiredArgsConstructor
public class UiConfigSemanticPatchService {

    public static final String SAFE = "SAFE";
    public static final String REVIEW = "REVIEW";
    public static final String BLOCKED = "BLOCKED";

    private static final Set<String> SAFE_FIELDS = Set.of(
            "formName", "listName", "description", "label", "fieldLabel",
            "fieldName",
            "helpText", "placeholder", "text", "content", "gridSpan",
            "width", "minWidth", "align", "fixed", "orderKey", "sortOrder",
            "pageSize", "emptyText", "title", "icon", "confirmText",
            "showOverflowTooltip", "defaultExpanded", "accordion",
            "layoutType", "labelWidth", "showPadding", "showBorder");
    private static final Set<String> REVIEW_FIELDS = Set.of(
            "readonly", "isReadonly", "hidden", "isHidden", "defaultValue",
            "required", "isRequired", "validation", "validationRules",
            "formatter", "componentVersion",
            "showInList");
    private static final Set<String> HIGH_RISK_FIELDS = Set.of(
            "id", "entityId", "formId", "formKey", "listKey", "fieldId",
            "fieldCode", "fieldType", "nodeKey", "nodeType", "bindingType",
            "bindingRef", "parentId", "componentType", "renderComponent",
            "dataSourceId", "dataSourceOperationCode",
            "queryInterfaceExtensionId", "queryOperationCode",
            "dataSourceBindingsDocument", "accessPermissionCode",
            "permissionCode", "queryType", "isQuery", "handlerCode",
            "actionCode", "customMode", "linkMode", "targetEntityCode",
            "targetListKey", "templateId", "templateVersion",
            "targetFormId", "targetFormMode",
            "targetFormReleaseId", "targetFormReleaseVersion",
            "childFormReleaseId", "childFormReleaseVersion",
            "refFormReleaseId", "refFormReleaseVersion",
            "publishedFormReleaseId", "publishedFormReleaseVersion",
            "relationId", "relationCode", "submitMapping", "beforeSubmit",
            "afterSubmit", "customComponent");
    private static final Set<String> JSON_DOCUMENT_FIELDS = Set.of(
            "propsDocument", "rulesDocument", "legacyPropsDocument",
            "localOverridesDocument", "viewConfig", "columnConfig",
            "queryConfig", "renderConfig", "selectionConfig",
            "fixedFilterConfig", "componentProps",
            "configDocument");

    private final JsonDocumentCodec codec;

    /**
     * 构建表单或列表发布快照之间的稳定 ID 语义补丁。
     *
     * @param configType 配置类型标识，决定后续界面配置语义补丁采用的处理分支
     * @param source 待构建界面配置语义补丁的原始输入，结果供调用方继续使用
     * @param target 目标，作为 {@code mapValue} 的输入影响后续处理
     * @return 构建后的界面配置语义补丁结果，供调用方继续处理
     */
    public PatchAnalysis build(
            String configType,
            Map<String, Object> source,
            Map<String, Object> target) {
        List<UiConfigSemanticPatchOperation> operations = new ArrayList<>();
        if (UiConfigReleaseService.FORM.equals(configType)) {
            diffMap(
                    "form",
                    "form",
                    mapValue(source.get("form")),
                    mapValue(target.get("form")),
                    "",
                    operations);
            diffCollection(
                    "nodes",
                    mapList(source.get("nodes")),
                    mapList(target.get("nodes")),
                    List.of("id", "nodeKey"),
                    operations);
            diffCollection(
                    "legacyFields",
                    mapList(source.get("legacyFields")),
                    mapList(target.get("legacyFields")),
                    List.of("id", "fieldCode"),
                    operations);
        } else {
            Map<String, Object> sourceList =
                    new LinkedHashMap<>(mapValue(source.get("list")));
            Map<String, Object> targetList =
                    new LinkedHashMap<>(mapValue(target.get("list")));
            List<Map<String, Object>> sourceFields =
                    removeMapList(sourceList, "fields");
            List<Map<String, Object>> targetFields =
                    removeMapList(targetList, "fields");
            List<Map<String, Object>> sourceToolbar =
                    removeMapList(sourceList, "toolbarConfig");
            List<Map<String, Object>> targetToolbar =
                    removeMapList(targetList, "toolbarConfig");
            List<Map<String, Object>> sourceRows =
                    removeMapList(sourceList, "rowActionConfig");
            List<Map<String, Object>> targetRows =
                    removeMapList(targetList, "rowActionConfig");
            diffMap("list", "list", sourceList, targetList, "", operations);
            diffCollection(
                    "fields",
                    sourceFields,
                    targetFields,
                    List.of("id", "fieldCode"),
                    operations);
            diffCollection(
                    "toolbarActions",
                    sourceToolbar,
                    targetToolbar,
                    List.of("id", "key", "actionCode"),
                    operations);
            diffCollection(
                    "rowActions",
                    sourceRows,
                    targetRows,
                    List.of("id", "key", "actionCode"),
                    operations);
        }
        diffCollection(
                "eventBindings",
                mapList(source.get("eventBindings")),
                mapList(target.get("eventBindings")),
                List.of("id"),
                operations);
        // 关联内容随宿主一起发布、撤销和热修复，不能只比较 FORM/LIST 主配置。
        diffCollection(
                "viewCompositions",
                mapList(source.get("viewCompositions")),
                mapList(target.get("viewCompositions")),
                List.of("id", "compositionKey"),
                operations);
        normalizeHotfixRisk(operations);
        operations.sort(Comparator
                .comparing(UiConfigSemanticPatchOperation::getSection)
                .thenComparing(UiConfigSemanticPatchOperation::getItemId)
                .thenComparing(UiConfigSemanticPatchOperation::getPath));
        String riskLevel = operations.stream()
                .map(UiConfigSemanticPatchOperation::getRiskLevel)
                .reduce(SAFE, this::maxRisk);
        List<UiConfigHotfixRiskItemDTO> risks = operations.stream()
                .map(item -> UiConfigHotfixRiskItemDTO.builder()
                        .section(item.getSection())
                        .itemId(item.getItemId())
                        .path(item.getPath())
                        .riskLevel(item.getRiskLevel())
                        .reason(item.getReason())
                        .build())
                .toList();
        return new PatchAnalysis(
                List.copyOf(operations),
                riskLevel,
                risks);
    }

    /**
     * 将语义补丁应用到某个流程版本原始或上一有效快照。
     *
     * @param baseSnapshot 基础快照，作为 {@code deepCopy} 的输入影响后续处理
     * @param operations 操作集合，供本方法应用界面配置语义补丁时使用
     * @param allowDivergedTarget 允许{@code diverged}目标，供本方法应用界面配置语义补丁时使用
     * @return 应用后的界面配置语义补丁结果，供调用方继续处理
     */
    public PatchApplication apply(
            Map<String, Object> baseSnapshot,
            List<UiConfigSemanticPatchOperation> operations,
            boolean allowDivergedTarget) {
        Map<String, Object> result = deepCopy(baseSnapshot);
        List<String> blockers = new ArrayList<>();
        boolean divergedTarget = false;
        for (UiConfigSemanticPatchOperation operation : operations) {
            if (isCollectionChange(operation)) {
                CollectionLocation collection = locateCollection(
                        result,
                        operation);
                if (collection == null) {
                    blockers.add(operation.getPath()
                            + "：目标版本缺少稳定配置集合");
                    continue;
                }
                Map<String, Object> current = findItem(
                        collection.items(),
                        operation.getItemId(),
                        collection.idKeys());
                boolean diverged = !equivalent(
                        current,
                        operation.getBeforeValue());
                divergedTarget = divergedTarget || diverged;
                if (diverged && !allowDivergedTarget) {
                    blockers.add(operation.getPath()
                            + "：目标版本内容已分歧");
                    continue;
                }
                if ("ADDED".equals(operation.getChangeType())) {
                    Map<String, Object> added = mapValue(
                            deepCopyValue(operation.getAfterValue()));
                    if (added.isEmpty()) {
                        blockers.add(operation.getPath()
                                + "：新增配置条目内容无效");
                        continue;
                    }
                    if (current != null) {
                        collection.items().remove(current);
                    }
                    collection.items().add(added);
                } else if (current != null) {
                    collection.items().remove(current);
                }
                continue;
            }
            ApplyLocation location = locate(result, operation);
            if (location == null) {
                blockers.add(operation.getPath() + "：目标版本缺少稳定条目或配置路径");
                continue;
            }
            Object currentValue = location.currentValue();
            boolean diverged = !equivalent(
                    currentValue,
                    operation.getBeforeValue());
            divergedTarget = divergedTarget || diverged;
            if (diverged && !allowDivergedTarget) {
                blockers.add(operation.getPath() + "：目标版本内容已分歧");
                continue;
            }
            location.write(deepCopyValue(operation.getAfterValue()));
        }
        return new PatchApplication(
                blockers.isEmpty() ? result : null,
                List.copyOf(blockers),
                divergedTarget);
    }

    /**
     * 规范化热修复风险；输出作为后续校验或处理的输入。
     *
     * @param operations 操作集合，供本方法规范化热修复风险时使用
     */
    private void normalizeHotfixRisk(
            List<UiConfigSemanticPatchOperation> operations) {
        for (UiConfigSemanticPatchOperation operation : operations) {
            if (!BLOCKED.equals(operation.getRiskLevel())) {
                continue;
            }
            operation.setRiskLevel(REVIEW);
            operation.setReason(isCollectionChange(operation)
                    ? "稳定配置条目发生变化，发布前需要复核"
                    : "高风险配置发生变化，发布前需要复核");
        }
    }

    /**
     * 判断是否集合变更；判断结果决定调用方的后续分支。
     *
     * @param operation 操作标识，决定后续集合变更采用的处理分支
     * @return 集合变更条件成立时为 true，否则为 false
     */
    private boolean isCollectionChange(
            UiConfigSemanticPatchOperation operation) {
        return "/".equals(operation.getPath())
                && Set.of("ADDED", "REMOVED").contains(
                        operation.getChangeType());
    }

    /**
     * 定位集合；结果供调用方的后续步骤使用。
     *
     * @param snapshot 快照，作为 {@code mapList} 的输入影响后续处理
     * @param operation 操作标识，决定后续集合采用的处理分支
     * @return 定位后的集合结果，供调用方继续处理
     */
    private CollectionLocation locateCollection(
            Map<String, Object> snapshot,
            UiConfigSemanticPatchOperation operation) {
        if ("eventBindings".equals(operation.getSection())) {
            List<Map<String, Object>> items =
                    mapList(snapshot.get("eventBindings"));
            snapshot.put("eventBindings", items);
            return new CollectionLocation(items, List.of("id"));
        }
        if ("viewCompositions".equals(operation.getSection())) {
            List<Map<String, Object>> items =
                    mapList(snapshot.get("viewCompositions"));
            snapshot.put("viewCompositions", items);
            return new CollectionLocation(
                    items,
                    List.of("id", "compositionKey"));
        }
        if ("nodes".equals(operation.getSection())
                || "legacyFields".equals(operation.getSection())) {
            String collection = operation.getSection();
            List<Map<String, Object>> items =
                    mapList(snapshot.get(collection));
            snapshot.put(collection, items);
            return new CollectionLocation(
                    items,
                    "legacyFields".equals(collection)
                            ? List.of("id", "fieldCode")
                            : List.of("id", "nodeKey"));
        }
        Map<String, Object> list = mapValue(snapshot.get("list"));
        snapshot.put("list", list);
        String collection = switch (operation.getSection()) {
            case "fields" -> "fields";
            case "toolbarActions" -> "toolbarConfig";
            case "rowActions" -> "rowActionConfig";
            default -> null;
        };
        if (collection == null) {
            return null;
        }
        List<Map<String, Object>> items = mapList(list.get(collection));
        list.put(collection, items);
        return new CollectionLocation(
                items,
                "fields".equals(operation.getSection())
                        ? List.of("id", "fieldCode")
                        : List.of("id", "key", "actionCode"));
    }

    /**
     * 写入补丁；后续读取或执行将使用更新后的状态。
     *
     * @param operations 操作集合，作为 {@code codec.write} 的输入影响后续处理
     * @return 写入后的补丁文本，供调用方比较或展示
     */
    public String writePatch(List<UiConfigSemanticPatchOperation> operations) {
        return codec.write(operations, "UI热修复语义补丁");
    }

    /**
     * 读取补丁；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code codec.read} 的输入影响后续处理
     * @return 界面配置语义补丁操作集合，供调用方遍历或展示
     */
    public List<UiConfigSemanticPatchOperation> readPatch(String document) {
        if (!StringUtils.hasText(document)) {
            return List.of();
        }
        return codec.read(
                document,
                new TypeReference<List<UiConfigSemanticPatchOperation>>() {},
                "UI热修复语义补丁");
    }

    /**
     * 处理差异集合，并将结果传给后续步骤。
     *
     * @param section 区段，作为 {@code operations.add} 的输入影响后续处理
     * @param sourceItems 来源条目，作为 {@code indexByStableId} 的输入影响后续处理
     * @param targetItems 目标条目，作为 {@code indexByStableId} 的输入影响后续处理
     * @param idKeys ID键集合，作为 {@code indexByStableId} 的输入影响后续处理
     * @param operations 操作集合，作为 {@code diffMap} 的输入影响后续处理
     */
    private void diffCollection(
            String section,
            List<Map<String, Object>> sourceItems,
            List<Map<String, Object>> targetItems,
            List<String> idKeys,
            List<UiConfigSemanticPatchOperation> operations) {
        Map<String, Map<String, Object>> sourceById =
                indexByStableId(sourceItems, idKeys);
        Map<String, Map<String, Object>> targetById =
                indexByStableId(targetItems, idKeys);
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(sourceById.keySet());
        ids.addAll(targetById.keySet());
        for (String id : ids) {
            Map<String, Object> source = sourceById.get(id);
            Map<String, Object> target = targetById.get(id);
            if (source == null || target == null) {
                operations.add(operation(
                        section,
                        id,
                        source == null ? "ADDED" : "REMOVED",
                        "/",
                        source,
                        target,
                        REVIEW,
                        "稳定配置条目发生新增或删除，发布前需要复核"));
                continue;
            }
            diffMap(section, id, source, target, "", operations);
        }
    }

    /**
     * 处理差异映射，并将结果传给后续步骤。
     *
     * @param section 区段，作为 {@code operations.add} 的输入影响后续处理
     * @param itemId 条目ID，后续用于处理差异映射时定位或关联目标
     * @param source 待处理差异映射的原始输入，结果供调用方继续使用
     * @param target 目标，作为 {@code keys.addAll} 的输入影响后续处理
     * @param prefix 前缀，供本方法处理差异映射时使用
     * @param operations 操作集合，供本方法处理差异映射时使用
     */
    private void diffMap(
            String section,
            String itemId,
            Map<String, Object> source,
            Map<String, Object> target,
            String prefix,
            List<UiConfigSemanticPatchOperation> operations) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(source.keySet());
        keys.addAll(target.keySet());
        for (String key : keys) {
            if (isVolatile(key)) {
                continue;
            }
            Object before = source.get(key);
            Object after = target.get(key);
            if (equivalent(before, after)) {
                continue;
            }
            String path = prefix + "/" + key;
            ParsedDocument documents = parseDocuments(key, before, after);
            if (documents != null) {
                diffMap(
                        section,
                        itemId,
                        documents.before(),
                        documents.after(),
                        path,
                        operations);
                continue;
            }
            if (before instanceof Map<?, ?> && after instanceof Map<?, ?>) {
                diffMap(
                        section,
                        itemId,
                        mapValue(before),
                        mapValue(after),
                        path,
                        operations);
                continue;
            }
            Risk risk = classify(path, before, after);
            operations.add(operation(
                    section,
                    itemId,
                    "UPDATED",
                    path,
                    before,
                    after,
                    risk.level(),
                    risk.reason()));
        }
    }

    /**
     * 解析{@code documents}；输出作为后续校验或处理的输入。
     *
     * @param field 字段，供本方法解析{@code documents}时使用
     * @param before 之前，作为 {@code ParsedDocument} 的输入影响后续处理
     * @param after 之后，供本方法解析{@code documents}时使用
     * @return 解析后的{@code documents}结果，供调用方继续处理
     */
    private ParsedDocument parseDocuments(
            String field,
            Object before,
            Object after) {
        if (!JSON_DOCUMENT_FIELDS.contains(field)
                || !isObjectDocumentValue(before)
                || !isObjectDocumentValue(after)) {
            return null;
        }
        try {
            return new ParsedDocument(
                    parseObjectDocumentValue(before),
                    parseObjectDocumentValue(after));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 判断是否对象文档值；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否对象文档值的原始输入，结果供调用方继续使用
     * @return 对象文档值条件成立时为 true，否则为 false
     */
    private boolean isObjectDocumentValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Map<?, ?>;
    }

    /**
     * 解析对象文档值；输出作为后续校验或处理的输入。
     *
     * @param value 待解析对象文档值的原始输入，结果供调用方继续使用
     * @return 对象文档值键值结果，供调用方继续处理
     */
    private Map<String, Object> parseObjectDocumentValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return mapValue(value);
        }
        return parseObjectDocument((String) value);
    }

    /**
     * 解析对象文档；输出作为后续校验或处理的输入。
     *
     * @param document 文档，供本方法解析对象文档时使用
     * @return 对象文档键值结果，供调用方继续处理
     */
    private Map<String, Object> parseObjectDocument(String document) {
        return StringUtils.hasText(document)
                ? codec.readObject(document, "UI配置嵌入文档")
                : new LinkedHashMap<>();
    }

    /**
     * 处理{@code classify}，并将结果传给后续步骤。
     *
     * @param path 路径，作为 {@code lastSegment} 的输入影响后续处理
     * @param before 之前，供本方法处理{@code classify}时使用
     * @param after 之后，供本方法处理{@code classify}时使用
     * @return 处理后的{@code classify}结果，供调用方继续处理
     */
    private Risk classify(String path, Object before, Object after) {
        String field = lastSegment(path);
        if (HIGH_RISK_FIELDS.contains(field)
                || containsHighRiskMarker(path)) {
            return new Risk(REVIEW, "涉及结构、绑定、权限、数据源或写操作语义，需要复核");
        }
        if (REVIEW_FIELDS.contains(field)) {
            return new Risk(REVIEW, "会改变运行时交互或校验行为，需要风险确认");
        }
        if (SAFE_FIELDS.contains(field)
                && !(before instanceof List<?>)
                && !(after instanceof List<?>)) {
            return new Risk(SAFE, "仅影响展示或稳定布局");
        }
        return new Risk(REVIEW, "未知配置路径需要复核，但不阻止热修复");
    }

    /**
     * 判断是否包含{@code high}风险{@code marker}；判断结果决定调用方的后续分支。
     *
     * @param path 路径，供本方法判断是否包含{@code high}风险{@code marker}时使用
     * @return {@code high}风险{@code marker}条件成立时为 true，否则为 false
     */
    private boolean containsHighRiskMarker(String path) {
        String normalized = path.toLowerCase();
        return normalized.contains("permission")
                || normalized.contains("datasource")
                || normalized.contains("relation")
                || normalized.contains("subform")
                || normalized.contains("submit")
                || normalized.contains("handler")
                || normalized.contains("connector")
                || normalized.contains("provider");
    }

    /**
     * 定位界面配置语义补丁；结果供调用方的后续步骤使用。
     *
     * @param snapshot 快照，作为 {@code mapList} 的输入影响后续处理
     * @param operation 操作标识，决定后续界面配置语义补丁采用的处理分支
     * @return 定位后的界面配置语义补丁结果，供调用方继续处理
     */
    private ApplyLocation locate(
            Map<String, Object> snapshot,
            UiConfigSemanticPatchOperation operation) {
        Map<String, Object> item;
        if ("eventBindings".equals(operation.getSection())) {
            List<Map<String, Object>> values =
                    mapList(snapshot.get("eventBindings"));
            snapshot.put("eventBindings", values);
            item = findItem(
                    values,
                    operation.getItemId(),
                    List.of("id"));
        } else if ("viewCompositions".equals(
                operation.getSection())) {
            List<Map<String, Object>> values =
                    mapList(snapshot.get("viewCompositions"));
            snapshot.put("viewCompositions", values);
            item = findItem(
                    values,
                    operation.getItemId(),
                    List.of("id", "compositionKey"));
        } else if ("form".equals(operation.getSection())) {
            item = mapValue(snapshot.get("form"));
            snapshot.put("form", item);
        } else if ("list".equals(operation.getSection())) {
            item = mapValue(snapshot.get("list"));
            snapshot.put("list", item);
        } else if (UiConfigReleaseService.FORM.equals(
                String.valueOf(snapshot.get("configType")))) {
            String collection = "legacyFields".equals(operation.getSection())
                    ? "legacyFields" : "nodes";
            List<Map<String, Object>> values =
                    mapList(snapshot.get(collection));
            snapshot.put(collection, values);
            item = findItem(
                    values,
                    operation.getItemId(),
                    "legacyFields".equals(collection)
                            ? List.of("id", "fieldCode")
                            : List.of("id", "nodeKey"));
        } else {
            Map<String, Object> list = mapValue(snapshot.get("list"));
            snapshot.put("list", list);
            String collection = switch (operation.getSection()) {
                case "fields" -> "fields";
                case "toolbarActions" -> "toolbarConfig";
                case "rowActions" -> "rowActionConfig";
                default -> null;
            };
            if (collection == null) {
                return null;
            }
            List<Map<String, Object>> values = mapList(list.get(collection));
            list.put(collection, values);
            item = findItem(
                    values,
                    operation.getItemId(),
                    "fields".equals(operation.getSection())
                            ? List.of("id", "fieldCode")
                            : List.of("id", "key", "actionCode"));
        }
        if (item == null) {
            return null;
        }
        return locatePath(item, operation.getPath());
    }

    /**
     * 定位路径；结果供调用方的后续步骤使用。
     *
     * @param root 根，供本方法定位路径时使用
     * @param path 路径，作为 {@code java.util.Arrays.stream} 的输入影响后续处理
     * @return 定位后的路径结果，供调用方继续处理
     */
    private ApplyLocation locatePath(
            Map<String, Object> root,
            String path) {
        List<String> segments = java.util.Arrays.stream(path.split("/"))
                .filter(StringUtils::hasText)
                .toList();
        if (segments.isEmpty()) {
            return null;
        }
        Map<String, Object> current = root;
        EmbeddedDocument embedded = null;
        for (int index = 0; index < segments.size() - 1; index++) {
            String segment = segments.get(index);
            Object child = current.get(segment);
            if (JSON_DOCUMENT_FIELDS.contains(segment)
                    && (child == null || child instanceof String)) {
                Map<String, Object> parsed;
                try {
                    parsed = parseObjectDocument((String) child);
                } catch (IllegalArgumentException exception) {
                    return null;
                }
                embedded = new EmbeddedDocument(current, segment, parsed);
                current = parsed;
                continue;
            }
            if (child == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(segment, created);
                current = created;
                continue;
            }
            if (!(child instanceof Map<?, ?>)) {
                return null;
            }
            Map<String, Object> mapped = mapValue(child);
            current.put(segment, mapped);
            current = mapped;
        }
        String leaf = segments.get(segments.size() - 1);
        Map<String, Object> target = current;
        EmbeddedDocument finalEmbedded = embedded;
        return new ApplyLocation(
                target.get(leaf),
                value -> {
                    if (value == null) {
                        target.remove(leaf);
                    } else {
                        target.put(leaf, value);
                    }
                    if (finalEmbedded != null) {
                        finalEmbedded.owner().put(
                                finalEmbedded.field(),
                                codec.write(
                                        finalEmbedded.document(),
                                        "热修复嵌入配置"));
                    }
                });
    }

    /**
     * 查询条目；查询结果供调用方展示或继续处理。
     *
     * @param items 条目，供本方法查询条目时使用
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param idKeys ID键集合，供本方法查询条目时使用
     * @return 条目键值结果，供调用方继续处理
     */
    private Map<String, Object> findItem(
            List<Map<String, Object>> items,
            String id,
            List<String> idKeys) {
        for (Map<String, Object> item : items) {
            if (id.equals(stableId(item, idKeys))) {
                return item;
            }
        }
        return null;
    }

    /**
     * 整理索引稳定ID数据，供调用方遍历或继续处理。
     *
     * @param items 条目，供本方法处理索引稳定ID时使用
     * @param idKeys ID键集合，作为 {@code stableId} 的输入影响后续处理
     * @return 索引稳定ID键值结果，供调用方继续处理
     */
    private Map<String, Map<String, Object>> indexByStableId(
            List<Map<String, Object>> items,
            List<String> idKeys) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String id = stableId(item, idKeys);
            if (StringUtils.hasText(id)) {
                indexed.put(id, item);
            }
        }
        return indexed;
    }

    /**
     * 生成稳定ID文本，供后续匹配或展示。
     *
     * @param item 条目，供本方法处理稳定ID时使用
     * @param idKeys ID键集合，供本方法处理稳定ID时使用
     * @return 处理后的稳定ID文本，供调用方比较或展示
     */
    private String stableId(
            Map<String, Object> item,
            List<String> idKeys) {
        for (String key : idKeys) {
            Object value = item.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 处理操作，并将结果传给后续步骤。
     *
     * @param section 区段，供本方法处理操作时使用
     * @param itemId 条目ID，后续用于处理操作时定位或关联目标
     * @param changeType 变更类型标识，决定后续操作采用的处理分支
     * @param path 路径，供本方法处理操作时使用
     * @param before 之前，作为 {@code deepCopyValue} 的输入影响后续处理
     * @param after 之后，供本方法处理操作时使用
     * @param risk 风险，供本方法处理操作时使用
     * @param reason 原因，供本方法处理操作时使用
     * @return 处理后的操作结果，供调用方继续处理
     */
    private UiConfigSemanticPatchOperation operation(
            String section,
            String itemId,
            String changeType,
            String path,
            Object before,
            Object after,
            String risk,
            String reason) {
        return UiConfigSemanticPatchOperation.builder()
                .section(section)
                .itemId(itemId)
                .changeType(changeType)
                .path(path)
                .beforeValue(deepCopyValue(before))
                .afterValue(deepCopyValue(after))
                .riskLevel(risk)
                .reason(reason)
                .build();
    }

    /**
     * 移除映射列表；后续读取或执行将使用更新后的状态。
     *
     * @param source 待移除映射列表的原始输入，结果供调用方继续使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 界面配置语义补丁集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> removeMapList(
            Map<String, Object> source,
            String key) {
        return mapList(source.remove(key));
    }

    /**
     * 整理{@code deep}副本数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code deep}副本的原始输入，结果供调用方继续使用
     * @return {@code deep}副本键值结果，供调用方继续处理
     */
    private Map<String, Object> deepCopy(Map<String, Object> value) {
        return codec.readObject(
                codec.write(value, "UI配置快照复制"),
                "UI配置快照复制");
    }

    /**
     * 处理{@code deep}副本值，并将结果传给后续步骤。
     *
     * @param value 待处理{@code deep}副本值的原始输入，结果供调用方继续使用
     * @return 处理后的{@code deep}副本值结果，供调用方继续处理
     */
    private Object deepCopyValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        return codec.read(
                codec.write(value, "UI语义补丁值"),
                "UI语义补丁值");
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param source 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param source 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 界面配置语义补丁集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> mapList(Object source) {
        if (!(source instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                result.add(mapValue(item));
            }
        }
        return result;
    }

    /**
     * 判断{@code equivalent}条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理{@code equivalent}时使用
     * @param right 右侧，作为 {@code codec.canonicalize} 的输入影响后续处理
     * @return {@code equivalent}条件成立时为 true，否则为 false
     */
    private boolean equivalent(Object left, Object right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        try {
            return Objects.equals(
                    codec.canonicalize(
                            codec.write(left, "UI配置差异左值"),
                            "UI配置差异左值"),
                    codec.canonicalize(
                            codec.write(right, "UI配置差异右值"),
                            "UI配置差异右值"));
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 判断是否{@code volatile}；判断结果决定调用方的后续分支。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return {@code volatile}条件成立时为 true，否则为 false
     */
    private boolean isVolatile(String key) {
        return Set.of(
                "revision", "activeReleaseId", "draftHash",
                "publishedVersion", "publishedSnapshot",
                "createTime", "updateTime", "createdAt", "updatedAt",
                "deleted").contains(key);
    }

    /**
     * 生成最大风险文本，供后续匹配或展示。
     *
     * @param left 左侧，供本方法处理最大风险时使用
     * @param right 右侧，供本方法处理最大风险时使用
     * @return 处理后的最大风险文本，供调用方比较或展示
     */
    private String maxRisk(String left, String right) {
        if (Set.of(REVIEW, BLOCKED).contains(left)
                || Set.of(REVIEW, BLOCKED).contains(right)) {
            return REVIEW;
        }
        return SAFE;
    }

    /**
     * 生成最后片段文本，供后续匹配或展示。
     *
     * @param path 路径，供本方法处理最后片段时使用
     * @return 处理后的最后片段文本，供调用方比较或展示
     */
    private String lastSegment(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    /**
     * 封装补丁{@code analysis}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param operations 操作集合，保存在对象中供后续校验、查询或展示
     * @param riskLevel 风险层级，保存在对象中供后续校验、查询或展示
     * @param riskItems 风险条目，保存在对象中供后续校验、查询或展示
     */
    public record PatchAnalysis(
            List<UiConfigSemanticPatchOperation> operations,
            String riskLevel,
            List<UiConfigHotfixRiskItemDTO> riskItems) {
    }

    /**
     * 封装补丁应用的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     * @param blockers 阻断项，保存在对象中供后续校验、查询或展示
     * @param diverged {@code diverged}，保存在对象中供后续校验、查询或展示
     */
    public record PatchApplication(
            Map<String, Object> snapshot,
            List<String> blockers,
            boolean diverged) {

        /**
         * 判断兼容条件是否成立，供调用方选择后续分支。
         *
         * @return 兼容条件成立时为 true，否则为 false
         */
        public boolean compatible() {
            return blockers == null || blockers.isEmpty();
        }
    }

    /**
     * 封装风险的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param level 层级，保存在对象中供后续校验、查询或展示
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    private record Risk(String level, String reason) {
    }

    /**
     * 封装{@code parsed}文档的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param before 之前，保存在对象中供后续校验、查询或展示
     * @param after 之后，保存在对象中供后续校验、查询或展示
     */
    private record ParsedDocument(
            Map<String, Object> before,
            Map<String, Object> after) {
    }

    /**
     * 封装集合{@code location}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param items 条目，保存在对象中供后续校验、查询或展示
     * @param idKeys ID键集合，保存在对象中供后续校验、查询或展示
     */
    private record CollectionLocation(
            List<Map<String, Object>> items,
            List<String> idKeys) {
    }

    /**
     * 封装{@code embedded}文档的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param owner 归属方，保存在对象中供后续校验、查询或展示
     * @param field 字段，保存在对象中供后续校验、查询或展示
     * @param document 文档，保存在对象中供后续校验、查询或展示
     */
    private record EmbeddedDocument(
            Map<String, Object> owner,
            String field,
            Map<String, Object> document) {
    }

    /**
     * 封装应用{@code location}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param currentValue 当前值，保存在对象中供后续校验、查询或展示
     * @param writer 写入器，保存在对象中供后续校验、查询或展示
     */
    private record ApplyLocation(
            Object currentValue,
            java.util.function.Consumer<Object> writer) {

        /**
         * 写入应用{@code location}；后续读取或执行将使用更新后的状态。
         *
         * @param value 待写入应用{@code location}的原始输入，结果供调用方继续使用
         */
        void write(Object value) {
            writer.accept(value);
        }
    }
}
