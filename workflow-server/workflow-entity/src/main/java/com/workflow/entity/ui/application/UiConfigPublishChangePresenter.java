package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.application.EntityFormFieldProjection;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.api.model.UiConfigSemanticPatchOperation;
import com.workflow.entity.ui.api.response.UiConfigPublishChangeDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 将发布补丁转换为业务可读信息；不修改补丁、快照或发布风险判定。 */
@RequiredArgsConstructor
public final class UiConfigPublishChangePresenter {
    private final JsonDocumentCodec codec;
    private final UiConfigSnapshotSupport snapshots;

    /** 只对已知的运行字段投影关系去重，新增属性默认保留，避免静默隐藏变化。 */
    private static final Map<String, List<String>> MIRROR_PATHS = Map.ofEntries(
            Map.entry("fieldCode", List.of("/propsDocument/fieldCode", "/nodeKey")),
            Map.entry("fieldId", List.of("/propsDocument/fieldId")),
            Map.entry("fieldName", List.of("/propsDocument/fieldName", "/propsDocument/label")),
            Map.entry("fieldLabel", List.of("/propsDocument/label", "/propsDocument/fieldName")),
            Map.entry("fieldType", List.of("/propsDocument/fieldType", "/nodeType")),
            Map.entry("componentType", List.of("/propsDocument/componentType", "/nodeType")),
            Map.entry("placeholder", List.of("/propsDocument/placeholder")),
            Map.entry("defaultValue", List.of("/propsDocument/defaultValue")),
            Map.entry("gridSpan", List.of("/propsDocument/gridSpan")),
            Map.entry("isRequired", List.of("/propsDocument/required")),
            Map.entry("isReadonly", List.of("/propsDocument/readonly")),
            Map.entry("isHidden", List.of("/propsDocument/hidden")),
            Map.entry("sortOrder", List.of("/orderKey")),
            Map.entry("componentProps", List.of("/propsDocument/componentProps")),
            Map.entry("validationRules", List.of("/rulesDocument/validation")),
            Map.entry("extensionConfig", List.of("/rulesDocument/extension")),
            Map.entry("dataSourceBindings", List.of("/dataSourceBindingsDocument")));

    /**
     * 保留每个真实语义操作，并从该操作所属的新旧快照中解析名称。
     * 表单兼容字段只有在节点操作覆盖同一变化且投影值完全一致时才省略。
     *
     * @param configType FORM 或 LIST
     * @param before 当前发布快照；首次发布为空
     * @param after 待发布草稿快照
     * @param operations 本次风险分析生成的原始补丁，不会被修改
     * @return 供确认页统计和展示的同源变更列表，未知内容按原样保留
     */
    public List<UiConfigPublishChangeDTO> present(
            String configType, Map<String, Object> before, Map<String, Object> after,
            List<UiConfigSemanticPatchOperation> operations) {
        Map<String, Map<String, Object>> oldProjection = "FORM".equals(configType)
                ? projectFields(before) : Map.of();
        Map<String, Map<String, Object>> newProjection = "FORM".equals(configType)
                ? projectFields(after) : Map.of();
        List<UiConfigPublishChangeDTO> details = new ArrayList<>();
        for (UiConfigSemanticPatchOperation operation : operations) {
            if ("FORM".equals(configType) && mirrored(operation, operations, oldProjection, newProjection)) {
                continue;
            }
            Map<String, Object> oldItem = item(before, operation);
            Map<String, Object> newItem = item(after, operation);
            String oldLabel = label(operation.getSection(), oldItem, operation.getItemId());
            String newLabel = label(operation.getSection(), newItem, operation.getItemId());
            details.add(UiConfigPublishChangeDTO.builder()
                    .section(operation.getSection())
                    .itemId(operation.getItemId())
                    .itemLabel("REMOVED".equals(operation.getChangeType()) ? oldLabel : newLabel)
                    .previousItemLabel(!oldItem.isEmpty() && !newItem.isEmpty()
                            && !Objects.equals(oldLabel, newLabel) ? oldLabel : null)
                    .changeType(operation.getChangeType())
                    .path(operation.getPath())
                    .beforeValue(operation.getBeforeValue())
                    .afterValue(operation.getAfterValue())
                    .beforeDisplayValue(displayValue(before, operation, oldItem))
                    .afterDisplayValue(displayValue(after, operation, newItem))
                    .riskLevel(operation.getRiskLevel())
                    .reason(operation.getReason())
                    .build());
        }
        return List.copyOf(details);
    }

    /**
     * 使用运行时同一投影规则验证镜像，且不传入旧字段元数据作为兜底。
     * 否则未知权限、校验或实体元数据可能被复制进投影而误判为节点已覆盖。
     */
    private Map<String, Map<String, Object>> projectFields(Map<String, Object> snapshot) {
        try {
            List<EntityFormNode> nodes = codec.read(codec.write(
                    list(snapshot.get("nodes")), "发布确认节点"),
                    new TypeReference<List<EntityFormNode>>() {}, "发布确认节点");
            if (nodes.stream().anyMatch(node -> node == null || node.getNodeType() == null)) {
                return Map.of();
            }
            String formId = text(map(snapshot.get("form")).get("id"));
            Object projected = snapshots.stableValue(new EntityFormFieldProjection(codec).derive(formId, nodes));
            Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
            for (Map<String, Object> field : list(projected)) {
                String id = first(field, "id", "fieldCode");
                if (id != null) indexed.put(id, field);
            }
            return indexed;
        } catch (IllegalArgumentException exception) {
            // 历史或未知节点结构不适合投影时只放弃去重，发布校验仍由原有链路负责。
            return Map.of();
        }
    }

    /** 只有同一稳定 ID、同一变化方向和前后投影值均可验证时才移除兼容镜像。 */
    private boolean mirrored(UiConfigSemanticPatchOperation operation,
            List<UiConfigSemanticPatchOperation> operations,
            Map<String, Map<String, Object>> before, Map<String, Map<String, Object>> after) {
        if (!"legacyFields".equals(operation.getSection())) return false;
        String path = operation.getPath();
        Map<String, Object> oldField = before.get(operation.getItemId());
        Map<String, Object> newField = after.get(operation.getItemId());
        if ("/".equals(path)) {
            // 整项增删必须覆盖所有属性；独有元数据或敏感规则有任何一项不同就完整保留。
            boolean covered = equivalent(operation.getBeforeValue(), oldField)
                    && equivalent(operation.getAfterValue(), newField);
            return covered && operations.stream().anyMatch(node -> sameNode(operation, node)
                    && "/".equals(node.getPath())
                    && Objects.equals(operation.getChangeType(), node.getChangeType()));
        }
        if (oldField == null || newField == null) return false;
        String property = path.substring(1).split("/", 2)[0];
        List<String> nodePaths = MIRROR_PATHS.get(property);
        if (nodePaths == null || !equivalent(operation.getBeforeValue(), valueAt(oldField, path))
                || !equivalent(operation.getAfterValue(), valueAt(newField, path))) return false;
        return operations.stream().anyMatch(node -> sameNode(operation, node)
                && "UPDATED".equals(node.getChangeType())
                && nodePaths.stream().anyMatch(prefix -> node.getPath().equals(prefix)
                        || node.getPath().startsWith(prefix + "/")));
    }

    private boolean sameNode(UiConfigSemanticPatchOperation field, UiConfigSemanticPatchOperation node) {
        return "nodes".equals(node.getSection()) && Objects.equals(field.getItemId(), node.getItemId());
    }

    /** 嵌入 JSON 的对象顺序不影响等价判断，解析失败则保留原值用于保守比较。 */
    private boolean equivalent(Object left, Object right) {
        return snapshots.equivalent(parsed(left), parsed(right));
    }

    private Object parsed(Object value) {
        if (value instanceof String string && (string.trim().startsWith("{") || string.trim().startsWith("["))) {
            try { return codec.read(string, "发布确认配置值"); }
            catch (IllegalArgumentException ignored) { return value; }
        }
        return value;
    }

    private Object valueAt(Map<String, Object> item, String path) {
        Object value = item;
        for (String part : path.substring(1).split("/")) {
            value = map(parsed(value)).get(part);
        }
        return value;
    }

    /** 使用与补丁相同的候选键顺序，禁止因名称相同而合并不同的控件。 */
    private Map<String, Object> item(Map<String, Object> snapshot, UiConfigSemanticPatchOperation operation) {
        String section = operation.getSection();
        if ("form".equals(section) || "list".equals(section)) return map(snapshot.get(section));
        return collection(snapshot, section).stream().filter(value -> Objects.equals(
                operation.getItemId(), identity(section, value))).findFirst().orElse(Map.of());
    }

    private List<Map<String, Object>> collection(Map<String, Object> snapshot, String section) {
        Object values = switch (section) {
            case "fields" -> map(snapshot.get("list")).get("fields");
            case "toolbarActions" -> map(snapshot.get("list")).get("toolbarConfig");
            case "rowActions" -> map(snapshot.get("list")).get("rowActionConfig");
            default -> snapshot.get(section);
        };
        return list(values);
    }

    private String identity(String section, Map<String, Object> value) {
        String[] keys = switch (section) {
            case "nodes" -> new String[]{"id", "nodeKey"};
            case "fields", "legacyFields" -> new String[]{"id", "fieldCode"};
            case "toolbarActions", "rowActions" -> new String[]{"id", "key", "actionCode"};
            case "viewCompositions" -> new String[]{"id", "compositionKey"};
            default -> new String[]{"id"};
        };
        return first(value, keys);
    }

    /**
     * 排列只在原快照内计算，节点限定同父级；跨容器移动分别计算前后位置。
     * 缺键或排序并列无法唯一定位时不推测，前端仍可查看原始技术值。
     */
    private String displayValue(Map<String, Object> snapshot, UiConfigSemanticPatchOperation operation,
            Map<String, Object> current) {
        if (current.isEmpty()) return null;
        String section = operation.getSection();
        if ("nodes".equals(section) && "/parentId".equals(operation.getPath())) {
            String parentId = parentId(current);
            if (parentId.isEmpty()) return "表单根布局";
            List<Map<String, Object>> parents = collection(snapshot, "nodes").stream()
                    .filter(node -> Objects.equals(parentId, identity("nodes", node))).toList();
            return parents.size() == 1 ? businessLabel("nodes", parents.get(0)) : null;
        }
        if (!List.of("/orderKey", "/sortOrder").contains(operation.getPath())
                || !List.of("nodes", "fields", "legacyFields", "toolbarActions", "rowActions", "viewCompositions")
                        .contains(section)) return null;
        List<Map<String, Object>> siblings = collection(snapshot, section).stream()
                .filter(value -> !"nodes".equals(section) || parentId(current).equals(parentId(value))).toList();
        if (siblings.stream().filter(value -> Objects.equals(operation.getItemId(), identity(section, value))).count() != 1) {
            return null;
        }
        // orderKey 是当前稀疏排序键；只有集合完全没有它时才使用历史 sortOrder / 按钮 sort。
        String primary = siblings.stream().anyMatch(value -> value.get("orderKey") != null) ? "orderKey" : "sortOrder";
        if ("sortOrder".equals(primary) && List.of("toolbarActions", "rowActions").contains(section)
                && siblings.stream().noneMatch(value -> value.get("sortOrder") != null)) primary = "sort";
        String sortKey = primary;
        if (siblings.stream().anyMatch(value -> number(value.get(sortKey)) == null)) return null;
        Comparator<Map<String, Object>> comparator = Comparator.comparing(value -> number(value.get(sortKey)));
        // 列表字段查询会用 sortOrder 打破 orderKey 并列；其他分区没有可靠次序时保守保留原值。
        if ("fields".equals(section) && "orderKey".equals(sortKey)
                && siblings.stream().allMatch(value -> number(value.get("sortOrder")) != null)) {
            comparator = comparator.thenComparing(value -> number(value.get("sortOrder")));
        }
        int preceding = 0;
        int equal = 0;
        for (Map<String, Object> sibling : siblings) {
            int comparison = comparator.compare(sibling, current);
            if (comparison < 0) preceding++;
            if (comparison == 0) equal++;
        }
        return equal == 1 ? "第 " + (preceding + 1) + " 项" : null;
    }

    private String parentId(Map<String, Object> node) {
        String parent = text(node.get("parentId"));
        return parent == null ? "" : parent.trim();
    }

    private BigDecimal number(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) return null;
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    /** 节点名称通常在 propsDocument 中；找不到业务名称时保留编码，避免空白行。 */
    private String label(String section, Map<String, Object> item, String fallback) {
        if ("form".equals(section)) return "表单设置";
        if ("list".equals(section)) return "列表设置";
        String name = businessLabel(section, item);
        if (name == null && "nodes".equals(section)) name = first(map(parsed(item.get("propsDocument"))), "fieldCode");
        if (name == null) name = first(item, "fieldCode", "nodeKey", "key", "actionCode", "compositionKey", "eventCode");
        return name == null ? fallback : name;
    }

    private String businessLabel(String section, Map<String, Object> item) {
        String name = first(item, "fieldLabel", "label", "fieldName", "name", "compositionName", "title");
        if (name == null && "nodes".equals(section)) {
            name = first(map(parsed(item.get("propsDocument"))), "label", "fieldLabel", "fieldName", "title");
        }
        return name;
    }

    private String first(Map<String, Object> value, String... keys) {
        for (String key : keys) {
            String result = text(value.get(key));
            if (StringUtils.hasText(result)) return result;
        }
        return null;
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }

    private Map<String, Object> map(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> source) source.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> values ? values.stream().map(this::map).toList() : List.of();
    }
}
