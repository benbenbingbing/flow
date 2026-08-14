package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从已部署 BPMN 的 assigneeConfig 扩展属性读取并校验下一审批人策略。
 */
@Component
@RequiredArgsConstructor
public class NextApproverSelectionPolicyReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public NextApprovalTarget read(
            String processDefinitionId,
            UserTask userTask) {
        Map<String, Object> assigneeConfig = readAssigneeConfig(userTask);
        Object rawSelection = assigneeConfig.get("nextApproverSelection");
        if (!(rawSelection instanceof Map<?, ?> rawMap)) {
            return new NextApprovalTarget(
                    userTask,
                    assigneeConfig,
                    NextApproverSelectionPolicy.absent());
        }
        Map<String, Object> selection = stringObjectMap(rawMap);
        int version = integerValue(selection.get("version"), 1);
        if (version != 1) {
            throw invalid(userTask,
                    "不支持的 nextApproverSelection 版本: " + version);
        }
        boolean visible = booleanValue(
                first(selection, "visible", "show", "display"), false);
        boolean editable = booleanValue(
                first(selection, "editable", "allowModify", "allowEdit"),
                false);
        String assignmentMode = assignmentMode(assigneeConfig, userTask);
        boolean multiple = !"DIRECT".equals(assignmentMode);
        if (editable && !visible) {
            throw invalid(userTask, "可修改时必须同时允许展示");
        }

        Map<String, Object> source = selection.get("source") instanceof Map<?, ?> rawSource
                ? stringObjectMap(rawSource)
                : Map.of();
        NextApproverSelectionPolicy.SourceType sourceType = sourceType(
                firstText(
                        source.get("type"),
                        selection.get("sourceType"),
                        selection.get("source") instanceof String
                                ? selection.get("source")
                                : null),
                userTask,
                editable);
        List<NextApproverSelectionPolicy.Scope> scopes = readScopes(
                selection, source, userTask);
        String resolverCode = firstText(
                source.get("resolverCode"),
                selection.get("resolverCode"),
                selection.get("interfaceName"));
        Map<String, Object> extraParams = readExtraParams(
                source.containsKey("extraParams")
                        ? source.get("extraParams")
                        : selection.get("extraParams"),
                userTask);

        if (editable && sourceType == null) {
            throw invalid(userTask, "可修改时必须配置 SCOPE 或 RESOLVER 数据源");
        }
        if ((visible || editable)
                && sourceType == NextApproverSelectionPolicy.SourceType.SCOPE
                && scopes.isEmpty()) {
            throw invalid(userTask, "SCOPE 数据源必须至少配置一个人员范围");
        }
        if ((visible || editable)
                && sourceType == NextApproverSelectionPolicy.SourceType.RESOLVER
                && !StringUtils.hasText(resolverCode)) {
            throw invalid(userTask, "RESOLVER 数据源必须配置 resolverCode");
        }

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("processDefinitionId", processDefinitionId);
        canonical.put("targetNodeId", userTask.getId());
        canonical.put("version", version);
        canonical.put("visible", visible);
        canonical.put("editable", editable);
        canonical.put("assignmentMode", assignmentMode);
        canonical.put("multiple", multiple);
        canonical.put("sourceType", sourceType == null ? null : sourceType.name());
        canonical.put("scopes", scopes.stream().map(scope -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("type", scope.type().name());
            value.put("values", scope.values());
            value.put("includeChildren", scope.includeChildren());
            return value;
        }).toList());
        canonical.put("resolverCode", resolverCode);
        canonical.put("extraParams", objectMapper.valueToTree(extraParams));

        return new NextApprovalTarget(
                userTask,
                assigneeConfig,
                new NextApproverSelectionPolicy(
                        true,
                        version,
                        visible,
                        editable,
                        assignmentMode,
                        multiple,
                        sourceType,
                        List.copyOf(scopes),
                        resolverCode,
                        Collections.unmodifiableMap(
                                new LinkedHashMap<>(extraParams)),
                        hash(canonical)));
    }

    private Map<String, Object> readAssigneeConfig(UserTask userTask) {
        String json = ConfiguredTaskPropertyReader.read(
                userTask, "assigneeConfig");
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw invalid(userTask, "assigneeConfig 不是合法 JSON", exception);
        }
    }

    private List<NextApproverSelectionPolicy.Scope> readScopes(
            Map<String, Object> selection,
            Map<String, Object> source,
            UserTask userTask) {
        List<NextApproverSelectionPolicy.Scope> result = new ArrayList<>();
        Object rawScopes = source.containsKey("rules")
                ? source.get("rules")
                : selection.get("scopes");
        if (rawScopes instanceof List<?> items) {
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> rawScope)) {
                    throw invalid(userTask, "scopes 中的范围必须是对象");
                }
                Map<String, Object> scope = stringObjectMap(rawScope);
                result.add(readScope(scope, userTask));
            }
        }
        if (result.isEmpty()
                && StringUtils.hasText(text(selection.get("scopeType")))) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("type", selection.get("scopeType"));
            legacy.put("values", first(
                    selection, "scopeValues", "values"));
            legacy.put("includeChildren", selection.get("includeChildren"));
            result.add(readScope(legacy, userTask));
        }
        return result;
    }

    private String assignmentMode(
            Map<String, Object> assigneeConfig,
            UserTask userTask) {
        String value = firstText(
                assigneeConfig.get("assignmentMode"),
                assigneeConfig.get("mode"));
        String normalized = null;
        if (StringUtils.hasText(value)) {
            normalized = switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "DIRECT" -> "DIRECT";
                case "CANDIDATE" -> "CANDIDATE";
                case "MULTI_INSTANCE" -> "MULTI_INSTANCE";
                default -> throw new IllegalArgumentException(
                        "不支持的审批分配模式: " + value);
            };
        }

        // Flowable 先按多实例循环创建实例任务；循环体中的 assignee
        // 不能把整个节点误判成普通直接任务。
        if (userTask.hasMultiInstanceLoopCharacteristics()) {
            return "MULTI_INSTANCE";
        }
        // 对普通 UserTask，明确 assignee 表示任务已直接分配。即使 BPMN
        // 同时保留 candidateUsers/candidateGroups，候选身份也只是候补，
        // 不能把人工覆盖降级为未认领的纯候选任务。
        if (StringUtils.hasText(userTask.getAssignee())) {
            return "DIRECT";
        }
        if (normalized != null) {
            return normalized;
        }
        if (booleanValue(assigneeConfig.get("multiInstance"), false)) {
            return "MULTI_INSTANCE";
        }
        String assigneeType = text(assigneeConfig.get("assigneeType"));
        if ((userTask.getCandidateUsers() != null
                && !userTask.getCandidateUsers().isEmpty())
                || (userTask.getCandidateGroups() != null
                && !userTask.getCandidateGroups().isEmpty())
                || "candidate".equalsIgnoreCase(assigneeType)
                || "role".equalsIgnoreCase(assigneeType)
                || "group".equalsIgnoreCase(assigneeType)) {
            return "CANDIDATE";
        }
        return "DIRECT";
    }

    private NextApproverSelectionPolicy.Scope readScope(
            Map<String, Object> scope,
            UserTask userTask) {
        String rawType = text(scope.get("type"));
        final NextApproverSelectionPolicy.ScopeType type;
        try {
            type = NextApproverSelectionPolicy.ScopeType.valueOf(
                    rawType == null
                            ? ""
                            : rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(userTask, "不支持的人员范围类型: " + rawType);
        }
        List<String> values = stringList(scope.get("values"));
        if (type != NextApproverSelectionPolicy.ScopeType.ALL_USERS
                && values.isEmpty()) {
            throw invalid(userTask, type.name() + " 人员范围不能为空");
        }
        return new NextApproverSelectionPolicy.Scope(
                type,
                List.copyOf(values),
                booleanValue(scope.get("includeChildren"), false));
    }

    private Map<String, Object> readExtraParams(
            Object value,
            UserTask userTask) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw invalid(userTask, "extraParams 必须是对象");
        }
        return stringObjectMap(rawMap);
    }

    private NextApproverSelectionPolicy.SourceType sourceType(
            Object value,
            UserTask userTask,
            boolean required) {
        String raw = text(value);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return NextApproverSelectionPolicy.SourceType.valueOf(
                    raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(userTask, "不支持的审批人数据源: " + raw);
        }
    }

    private String hash(Map<String, Object> canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writer()
                    .with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(canonical)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("下一审批人范围签名失败", exception);
        }
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::text)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
        String single = text(value);
        return StringUtils.hasText(single)
                ? List.of(single.trim())
                : List.of();
    }

    private Map<String, Object> stringObjectMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Object first(Map<String, Object> values, String... names) {
        for (String name : names) {
            if (values.containsKey(name) && values.get(name) != null) {
                return values.get(name);
            }
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String result = text(value);
            if (StringUtils.hasText(result)) {
                return result.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private int integerValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "nextApproverSelection.version 必须是整数");
        }
    }

    private IllegalArgumentException invalid(UserTask task, String detail) {
        return invalid(task, detail, null);
    }

    private IllegalArgumentException invalid(
            UserTask task,
            String detail,
            Exception cause) {
        String message = "下一审批人配置无效: nodeId="
                + task.getId()
                + ", "
                + detail;
        return cause == null
                ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }
}
