package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver.ResolvedAssignment;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.springframework.beans.factory.annotation.Autowired;
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
public class NextApproverSelectionPolicyReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final NodeAssignmentReferenceResolver referenceResolver;

    @Autowired
    public NextApproverSelectionPolicyReader(
            ObjectMapper objectMapper,
            NodeAssignmentReferenceResolver referenceResolver) {
        this.objectMapper = objectMapper;
        this.referenceResolver = referenceResolver;
    }

    /** 兼容不涉及节点引用的轻量单元测试。 */
    public NextApproverSelectionPolicyReader(ObjectMapper objectMapper) {
        this(objectMapper,
                new NodeAssignmentReferenceResolver(objectMapper));
    }

    public NextApprovalTarget read(
            String processDefinitionId,
            UserTask userTask) {
        return read(processDefinitionId, userTask, null);
    }

    /**
     * 读取策略并在同一已部署模型中解析基础办理人的节点引用。
     */
    public NextApprovalTarget read(
            String processDefinitionId,
            UserTask userTask,
            BpmnModel bpmnModel) {
        Map<String, Object> currentConfig = readAssigneeConfig(userTask);
        ResolvedAssignment resolved = resolveAssignment(
                bpmnModel, userTask, currentConfig);
        Map<String, Object> assigneeConfig = resolved.assigneeConfig();
        UserTask assignmentSourceTask = resolved.sourceTask();
        Object rawSelection = currentConfig.get("nextApproverSelection");
        if (!(rawSelection instanceof Map<?, ?> rawMap)) {
            return new NextApprovalTarget(
                    userTask,
                    assigneeConfig,
                    NextApproverSelectionPolicy.absent(),
                    assignmentSourceTask);
        }
        Map<String, Object> selection = stringObjectMap(rawMap);
        NextApproverSelectionNormalizer.NormalizedSelection normalized;
        try {
            normalized = NextApproverSelectionNormalizer.normalize(
                    selection);
        } catch (IllegalArgumentException exception) {
            throw invalid(userTask, exception.getMessage(), exception);
        }
        int version = normalized.version();
        if (version != 1) {
            throw invalid(userTask,
                    "不支持的 nextApproverSelection 版本: " + version);
        }
        boolean visible = normalized.visible();
        boolean editable = normalized.editable();
        String assignmentMode = assignmentMode(
                assigneeConfig, userTask, assignmentSourceTask);
        boolean multiple = !"DIRECT".equals(assignmentMode);
        if (editable && !visible) {
            throw invalid(userTask, "可修改时必须同时允许展示");
        }

        NextApproverSelectionPolicy.SourceType sourceType = sourceType(
                normalized.sourceType(),
                userTask);
        List<NextApproverSelectionPolicy.Scope> scopes = readScopes(
                normalized.rawScopes(), userTask);
        String resolverCode = normalized.resolverCode();
        Map<String, Object> extraParams = readExtraParams(
                normalized.extraParams(),
                userTask);

        if (editable && sourceType == null) {
            throw invalid(
                    userTask,
                    "可修改时必须配置 SCOPE、RESOLVER 或 NODE_ASSIGNMENT 数据源");
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
        if (sourceType
                == NextApproverSelectionPolicy.SourceType.NODE_ASSIGNMENT) {
            // NODE_ASSIGNMENT 的允许范围来自目标节点自身。将真正影响人员
            // 展开的基础/历史字段及 BPMN 分配属性纳入签名，配置变化后旧
            // scopeKey 不能继续提交覆盖。
            canonical.put(
                    "nodeAssignment",
                    canonicalNodeAssignment(
                            assigneeConfig, assignmentSourceTask));
            canonical.put(
                    "assignmentReferenceChain",
                    resolved.chainNodeIds());
        }

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
                        hash(canonical)),
                assignmentSourceTask);
    }

    private ResolvedAssignment resolveAssignment(
            BpmnModel bpmnModel,
            UserTask userTask,
            Map<String, Object> currentConfig) {
        if (bpmnModel == null) {
            if (NodeAssignmentReferenceResolver.isNodeReference(
                    currentConfig)) {
                throw invalid(userTask,
                        "解析 node_reference 必须提供已部署 BpmnModel");
            }
            return new ResolvedAssignment(
                    userTask, currentConfig, List.of(userTask.getId()));
        }
        return referenceResolver.resolve(
                bpmnModel, userTask, currentConfig);
    }

    private Map<String, Object> canonicalNodeAssignment(
            Map<String, Object> config,
            UserTask userTask) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> keys = List.of(
                "assignmentConfigVersion",
                "assigneeType",
                "assigneeValue",
                "candidateUsers",
                "candidateGroups",
                "resolverCode",
                "interfaceName",
                "extraParams",
                // 未声明 v2 的多实例部署仍按这些历史字段解析。
                "multiInstanceUsers",
                "multiInstanceUserIds",
                "multiInstanceUsernames",
                "multiInstanceGroupIds",
                "multiInstanceGroupCodes",
                "multiInstanceRoleIds",
                "multiInstanceRoleCodes",
                "collectionSource",
                "collectionResolverCode",
                "collectionInterface",
                "collectionExtraParams");
        for (String key : keys) {
            if (config.containsKey(key)) {
                result.put(key, config.get(key));
            }
        }
        result.put("bpmnAssignee", userTask.getAssignee());
        result.put(
                "bpmnCandidateUsers",
                userTask.getCandidateUsers() == null
                        ? List.of() : userTask.getCandidateUsers());
        result.put(
                "bpmnCandidateGroups",
                userTask.getCandidateGroups() == null
                        ? List.of() : userTask.getCandidateGroups());
        return result;
    }

    private Map<String, Object> readAssigneeConfig(UserTask userTask) {
        Map<String, Object> assigneeConfig = readConfigProperty(
                userTask, "assigneeConfig");
        Map<String, Object> multiInstanceConfig = readConfigProperty(
                userTask, "multiInstanceConfig");
        return LegacyMultiInstanceAssignmentParser.mergeConfigs(
                assigneeConfig, multiInstanceConfig);
    }

    private Map<String, Object> readConfigProperty(
            UserTask userTask,
            String propertyName) {
        String json = ConfiguredTaskPropertyReader.read(
                userTask, propertyName);
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw invalid(
                    userTask,
                    propertyName + " 不是合法 JSON",
                    exception);
        }
    }

    private List<NextApproverSelectionPolicy.Scope> readScopes(
            Object rawScopes,
            UserTask userTask) {
        List<NextApproverSelectionPolicy.Scope> result = new ArrayList<>();
        if (rawScopes instanceof List<?> items) {
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> rawScope)) {
                    throw invalid(userTask, "scopes 中的范围必须是对象");
                }
                Map<String, Object> scope = stringObjectMap(rawScope);
                result.add(readScope(scope, userTask));
            }
        }
        return result;
    }

    private String assignmentMode(
            Map<String, Object> assigneeConfig,
            UserTask userTask,
            UserTask assignmentSourceTask) {
        return NodeAssignmentReferenceResolver.assignmentMode(
                userTask, assignmentSourceTask, assigneeConfig);
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
            UserTask userTask) {
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
