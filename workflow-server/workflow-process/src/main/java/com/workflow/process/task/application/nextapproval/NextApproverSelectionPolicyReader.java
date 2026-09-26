package com.workflow.process.task.application.nextapproval;

import com.workflow.process.task.application.nextapproval.model.NextApprovalTarget;
import com.workflow.process.task.application.nextapproval.model.NextApproverSelectionPolicy;

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

    /**
     * 初始化下一步审批人选择策略{@code reader}，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param referenceResolver 引用解析器依赖，保存到当前对象供后续业务方法调用
     */
    @Autowired
    public NextApproverSelectionPolicyReader(
            ObjectMapper objectMapper,
            NodeAssignmentReferenceResolver referenceResolver) {
        this.objectMapper = objectMapper;
        this.referenceResolver = referenceResolver;
    }

    /**
     * 兼容不涉及节点引用的轻量单元测试。
     *
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     */
    public NextApproverSelectionPolicyReader(ObjectMapper objectMapper) {
        this(objectMapper,
                new NodeAssignmentReferenceResolver(objectMapper));
    }

    /**
     * 读取下一步审批人选择策略{@code reader}；查询结果供调用方展示或继续处理。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param userTask 用户任务，供本方法读取下一步审批人选择策略{@code reader}时使用
     * @return 读取后的下一步审批人选择策略{@code reader}结果，供调用方继续处理
     */
    public NextApprovalTarget read(
            String processDefinitionId,
            UserTask userTask) {
        return read(processDefinitionId, userTask, null);
    }

    /**
     * 读取策略并在同一已部署模型中解析基础办理人的节点引用。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param userTask 用户任务，作为 {@code readAssigneeConfig} 的输入影响后续处理
     * @param bpmnModel BPMN模型，作为 {@code resolveAssignment} 的输入影响后续处理
     * @return 读取后的下一步审批人选择策略{@code reader}结果，供调用方继续处理
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

    /**
     * 解析分配；输出作为后续校验或处理的输入。
     *
     * @param bpmnModel BPMN模型，作为 {@code referenceResolver.resolve} 的输入影响后续处理
     * @param userTask 用户任务，作为 {@code invalid} 的输入影响后续处理
     * @param currentConfig 当前配置内容，决定后续分配的处理规则
     * @return 解析后的分配结果，供调用方继续处理
     */
    private ResolvedAssignment resolveAssignment(
            BpmnModel bpmnModel,
            UserTask userTask,
            Map<String, Object> currentConfig) {
        if (bpmnModel == null) {
            if (NodeAssignmentReferenceResolver.isEffectiveNodeReference(
                    currentConfig,
                    userTask.hasMultiInstanceLoopCharacteristics())) {
                throw invalid(userTask,
                        "解析 node_reference 必须提供已部署 BpmnModel");
            }
            return new ResolvedAssignment(
                    userTask, currentConfig, List.of(userTask.getId()));
        }
        return referenceResolver.resolve(
                bpmnModel, userTask, currentConfig);
    }

    /**
     * 整理规范节点分配数据，供调用方遍历或继续处理。
     *
     * @param config 配置内容，决定后续规范节点分配的处理规则
     * @param userTask 用户任务，作为 {@code result.put} 的输入影响后续处理
     * @return 规范节点分配键值结果，供调用方继续处理
     */
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

    /**
     * 读取办理人配置；查询结果供调用方展示或继续处理。
     *
     * @param userTask 用户任务，作为 {@code readConfigProperty} 的输入影响后续处理
     * @return 办理人配置键值结果，供调用方继续处理
     */
    private Map<String, Object> readAssigneeConfig(UserTask userTask) {
        Map<String, Object> assigneeConfig = readConfigProperty(
                userTask, "assigneeConfig");
        Map<String, Object> multiInstanceConfig = readConfigProperty(
                userTask, "multiInstanceConfig");
        return LegacyMultiInstanceAssignmentParser.mergeConfigs(
                assigneeConfig, multiInstanceConfig);
    }

    /**
     * 读取配置属性；查询结果供调用方展示或继续处理。
     *
     * @param userTask 用户任务，作为 {@code ConfiguredTaskPropertyReader.read} 的输入影响后续处理
     * @param propertyName 属性名称，后续用于读取配置属性时匹配或展示
     * @return 配置属性键值结果，供调用方继续处理
     */
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

    /**
     * 读取{@code scopes}；查询结果供调用方展示或继续处理。
     *
     * @param rawScopes 原始{@code scopes}，供本方法读取{@code scopes}时使用
     * @param userTask 用户任务，作为 {@code invalid} 的输入影响后续处理
     * @return 下一步审批人选择策略集合，供调用方遍历或展示
     */
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

    /**
     * 生成分配模式文本，供后续匹配或展示。
     *
     * @param assigneeConfig 办理人配置内容，决定后续分配模式的处理规则
     * @param userTask 用户任务，供本方法处理分配模式时使用
     * @param assignmentSourceTask 分配来源任务，供本方法处理分配模式时使用
     * @return 处理后的分配模式文本，供调用方比较或展示
     */
    private String assignmentMode(
            Map<String, Object> assigneeConfig,
            UserTask userTask,
            UserTask assignmentSourceTask) {
        return NodeAssignmentReferenceResolver.assignmentMode(
                userTask, assignmentSourceTask, assigneeConfig);
    }

    /**
     * 读取作用域；查询结果供调用方展示或继续处理。
     *
     * @param scope 作用域，作为 {@code text} 的输入影响后续处理
     * @param userTask 用户任务，作为 {@code invalid} 的输入影响后续处理
     * @return 读取后的作用域结果，供调用方继续处理
     */
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

    /**
     * 读取附加参数；查询结果供调用方展示或继续处理。
     *
     * @param value 待读取附加参数的原始输入，结果供调用方继续使用
     * @param userTask 用户任务，作为 {@code invalid} 的输入影响后续处理
     * @return 附加参数键值结果，供调用方继续处理
     */
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

    /**
     * 处理来源类型，并将结果传给后续步骤。
     *
     * @param value 待处理来源类型的原始输入，结果供调用方继续使用
     * @param userTask 用户任务，作为 {@code invalid} 的输入影响后续处理
     * @return 处理后的来源类型结果，供调用方继续处理
     */
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

    /**
     * 生成哈希文本，供后续匹配或展示。
     *
     * @param canonical 规范，供本方法处理哈希时使用
     * @return 处理后的哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 整理字符串列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串列表的原始输入，结果供调用方继续使用
     * @return 下一步审批人选择策略{@code reader}集合，供调用方遍历或展示
     */
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

    /**
     * 整理字符串对象映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串对象映射的原始输入，结果供调用方继续使用
     * @return 字符串对象映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringObjectMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
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
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    private boolean booleanValue(Object value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param task 任务，供本方法处理无效时使用
     * @param detail 详情，供本方法处理无效时使用
     * @return 处理后的无效结果，供调用方继续处理
     */
    private IllegalArgumentException invalid(UserTask task, String detail) {
        return invalid(task, detail, null);
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param task 任务，供本方法处理无效时使用
     * @param detail 详情，供本方法处理无效时使用
     * @param cause 原因，供本方法处理无效时使用
     * @return 处理后的无效结果，供调用方继续处理
     */
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
