package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiConfigHotfixRiskItemDTO;
import com.workflow.dto.UiConfigSemanticPatchOperation;
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
            "layoutType", "labelWidth");
    private static final Set<String> REVIEW_FIELDS = Set.of(
            "readonly", "isReadonly", "hidden", "isHidden", "defaultValue",
            "required", "isRequired", "validation", "validationRules",
            "formatter", "componentVersion",
            "showInList");
    private static final Set<String> HIGH_RISK_FIELDS = Set.of(
            "id", "entityId", "formId", "formKey", "listKey", "fieldId",
            "fieldCode", "fieldType", "nodeKey", "nodeType", "bindingType",
            "bindingRef", "parentId", "componentType", "renderComponent",
            "dataSourceId", "queryDataSourceId",
            "dataSourceBindingsDocument", "accessPermissionCode",
            "permissionCode", "queryType", "isQuery", "handlerCode",
            "actionCode", "customMode", "linkMode", "targetEntityCode",
            "targetListKey", "templateId", "templateVersion",
            "childFormReleaseId", "childFormReleaseVersion",
            "refFormReleaseId", "refFormReleaseVersion",
            "publishedFormReleaseId", "publishedFormReleaseVersion",
            "relationId", "relationCode", "submitMapping", "beforeSubmit",
            "afterSubmit", "customComponent");
    private static final Set<String> JSON_DOCUMENT_FIELDS = Set.of(
            "propsDocument", "rulesDocument", "legacyPropsDocument",
            "localOverridesDocument", "viewConfig", "columnConfig",
            "queryConfig", "renderConfig", "selectionConfig",
            "fixedFilterConfig", "contextBindingConfig", "initConfig",
            "componentProps");

    private final JsonDocumentCodec codec;

    /**
     * 构建表单或列表发布快照之间的稳定 ID 语义补丁。
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
            Object sourceScenes = sourceList.remove("allowedScenes");
            Object targetScenes = targetList.remove("allowedScenes");
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
            if (!equivalent(sourceScenes, targetScenes)) {
                operations.add(operation(
                        "allowedScenes",
                        "allowedScenes",
                        "UPDATED",
                        "/allowedScenes",
                        sourceScenes,
                        targetScenes,
                        REVIEW,
                        "列表适用场景变化会改变运行入口，发布前需要复核"));
            }
        }
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

    private boolean isCollectionChange(
            UiConfigSemanticPatchOperation operation) {
        return "/".equals(operation.getPath())
                && Set.of("ADDED", "REMOVED").contains(
                        operation.getChangeType());
    }

    private CollectionLocation locateCollection(
            Map<String, Object> snapshot,
            UiConfigSemanticPatchOperation operation) {
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

    public String writePatch(List<UiConfigSemanticPatchOperation> operations) {
        return codec.write(operations, "UI热修复语义补丁");
    }

    public List<UiConfigSemanticPatchOperation> readPatch(String document) {
        if (!StringUtils.hasText(document)) {
            return List.of();
        }
        return codec.read(
                document,
                new TypeReference<List<UiConfigSemanticPatchOperation>>() {},
                "UI热修复语义补丁");
    }

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

    private boolean isObjectDocumentValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Map<?, ?>;
    }

    private Map<String, Object> parseObjectDocumentValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return mapValue(value);
        }
        return parseObjectDocument((String) value);
    }

    private Map<String, Object> parseObjectDocument(String document) {
        return StringUtils.hasText(document)
                ? codec.readObject(document, "UI配置嵌入文档")
                : new LinkedHashMap<>();
    }

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

    private ApplyLocation locate(
            Map<String, Object> snapshot,
            UiConfigSemanticPatchOperation operation) {
        Map<String, Object> item;
        if ("form".equals(operation.getSection())) {
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

    private List<Map<String, Object>> removeMapList(
            Map<String, Object> source,
            String key) {
        return mapList(source.remove(key));
    }

    private Map<String, Object> deepCopy(Map<String, Object> value) {
        return codec.readObject(
                codec.write(value, "UI配置快照复制"),
                "UI配置快照复制");
    }

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

    private Map<String, Object> mapValue(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

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

    private boolean isVolatile(String key) {
        return Set.of(
                "revision", "activeReleaseId", "draftHash",
                "publishedVersion", "publishedSnapshot",
                "createTime", "updateTime", "createdAt", "updatedAt",
                "deleted").contains(key);
    }

    private String maxRisk(String left, String right) {
        if (Set.of(REVIEW, BLOCKED).contains(left)
                || Set.of(REVIEW, BLOCKED).contains(right)) {
            return REVIEW;
        }
        return SAFE;
    }

    private String lastSegment(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    public record PatchAnalysis(
            List<UiConfigSemanticPatchOperation> operations,
            String riskLevel,
            List<UiConfigHotfixRiskItemDTO> riskItems) {
    }

    public record PatchApplication(
            Map<String, Object> snapshot,
            List<String> blockers,
            boolean diverged) {

        public boolean compatible() {
            return blockers == null || blockers.isEmpty();
        }
    }

    private record Risk(String level, String reason) {
    }

    private record ParsedDocument(
            Map<String, Object> before,
            Map<String, Object> after) {
    }

    private record CollectionLocation(
            List<Map<String, Object>> items,
            List<String> idKeys) {
    }

    private record EmbeddedDocument(
            Map<String, Object> owner,
            String field,
            Map<String, Object> document) {
    }

    private record ApplyLocation(
            Object currentValue,
            java.util.function.Consumer<Object> writer) {

        void write(Object value) {
            writer.accept(value);
        }
    }
}
