package com.workflow.process.assignment.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在单个已部署 BPMN 模型内递归解析“使用其他节点审批人”。
 *
 * <p>{@code referencedNodeId} 是唯一权威引用；节点名称仅用于设计器回显。
 * 解析过程不访问当前草稿或节点快照，确保旧实例始终使用它绑定的部署版本。</p>
 */
@Component
@RequiredArgsConstructor
public class NodeAssignmentReferenceResolver {

    public static final int MAX_REFERENCE_DEPTH = 16;

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    /**
     * 从当前节点开始解析到真正提供办理人规则的 UserTask。
     *
     * @param model 同一流程定义的已部署模型
     * @param currentTask 当前被分配的节点
     * @param currentConfig 已从当前节点读取并合并历史字段的配置
     * @return 终端规则节点、终端配置及完整节点链
     * @throws IllegalArgumentException 引用缺失、越界、成环或配置损坏时抛出
     */
    public ResolvedAssignment resolve(
            BpmnModel model,
            UserTask currentTask,
            Map<String, Object> currentConfig) {
        if (model == null || model.getMainProcess() == null) {
            throw new IllegalArgumentException("无法读取引用节点所在的部署模型");
        }
        if (currentTask == null || !StringUtils.hasText(currentTask.getId())) {
            throw new IllegalArgumentException("当前审批节点缺少稳定 nodeId");
        }

        UserTask task = currentTask;
        Map<String, Object> config = immutableCopy(currentConfig);
        Set<String> visited = new LinkedHashSet<>();
        List<String> chain = new ArrayList<>();
        for (int depth = 0; depth <= MAX_REFERENCE_DEPTH; depth++) {
            String nodeId = task.getId();
            if (!visited.add(nodeId)) {
                chain.add(nodeId);
                throw invalid(currentTask,
                        "审批人节点引用形成环: " + String.join(" -> ", chain));
            }
            chain.add(nodeId);
            if (!isEffectiveNodeReference(
                    config,
                    task.hasMultiInstanceLoopCharacteristics())) {
                return new ResolvedAssignment(
                        task,
                        config,
                        List.copyOf(chain));
            }
            if (depth == MAX_REFERENCE_DEPTH) {
                throw invalid(currentTask,
                        "审批人节点引用超过最大深度 " + MAX_REFERENCE_DEPTH);
            }

            String referencedNodeId = referencedNodeId(config);
            if (!StringUtils.hasText(referencedNodeId)) {
                throw invalid(task, "node_reference 缺少 referencedNodeId");
            }
            if (containsExpression(referencedNodeId)) {
                throw invalid(task,
                        "referencedNodeId 必须是字面量节点 ID，不能使用表达式");
            }
            FlowElement element = model.getMainProcess().getFlowElement(
                    referencedNodeId, true);
            if (!(element instanceof UserTask referencedTask)) {
                throw invalid(task,
                        "referencedNodeId 不存在或不是 UserTask: "
                                + referencedNodeId);
            }
            task = referencedTask;
            config = readAssigneeConfig(referencedTask);
        }
        throw invalid(currentTask,
                "审批人节点引用超过最大深度 " + MAX_REFERENCE_DEPTH);
    }

    /**
     * 读取 assigneeConfig，并与同一部署中的历史多实例配置保序合并。
     *
     * @param userTask 用户任务，作为 {@code readConfigProperty} 的输入影响后续处理
     * @return 办理人配置键值结果，供调用方继续处理
     */
    public Map<String, Object> readAssigneeConfig(UserTask userTask) {
        Map<String, Object> assigneeConfig = readConfigProperty(
                userTask, "assigneeConfig");
        Map<String, Object> multiInstanceConfig = readConfigProperty(
                userTask, "multiInstanceConfig");
        return immutableCopy(
                LegacyMultiInstanceAssignmentParser.mergeConfigs(
                        assigneeConfig, multiInstanceConfig));
    }

    /**
     * 兼容大小写及历史驼峰写法，但新部署统一保存 node_reference。
     *
     * @param config 配置内容，决定后续节点引用的处理规则
     * @return 节点引用条件成立时为 true，否则为 false
     */
    public static boolean isNodeReference(Map<String, Object> config) {
        String type = text(config == null
                ? null : config.get("assigneeType"));
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String normalized = type.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return "nodereference".equals(normalized);
    }

    /**
     * 节点引用与历史多实例来源共存时遵循统一 legacy-first 规则。
     *
     * @param config 配置内容，决定后续有效节点引用的处理规则
     * @param multiInstanceSource 多实例来源，作为 {@code usesLegacyMultiInstanceAssignment} 的输入影响后续处理
     * @return 有效节点引用条件成立时为 true，否则为 false
     */
    public static boolean isEffectiveNodeReference(
            Map<String, Object> config,
            boolean multiInstanceSource) {
        return !LegacyMultiInstanceAssignmentParser
                .usesLegacyMultiInstanceAssignment(
                        config, multiInstanceSource)
                && isNodeReference(config);
    }

    /**
     * canonical 字段优先，历史 sourceNodeId 仅作读取兼容。
     *
     * @param config 配置内容，决定后续已引用节点ID的处理规则
     * @return 处理后的已引用节点ID文本，供调用方比较或展示
     */
    public static String referencedNodeId(Map<String, Object> config) {
        return firstText(
                config == null ? null : config.get("referencedNodeId"),
                config == null ? null : config.get("sourceNodeId"));
    }

    /**
     * 统一计算引用者的实际输出模式。当前节点是否多实例最优先；普通任务
     * 继承源规则的 direct/candidate 语义，但绝不继承源节点的循环属性。
     *
     * @param currentTask 当前任务，写入当前任务信息供后续待办展示和状态同步
     * @param sourceTask 来源任务，供本方法处理分配模式时使用
     * @param sourceConfig 来源配置内容，决定后续分配模式的处理规则
     * @return 处理后的分配模式文本，供调用方比较或展示
     */
    public static String assignmentMode(
            UserTask currentTask,
            UserTask sourceTask,
            Map<String, Object> sourceConfig) {
        return assignmentMode(
                currentTask.hasMultiInstanceLoopCharacteristics(),
                sourceTask.hasMultiInstanceLoopCharacteristics(),
                sourceTask.getAssignee(),
                sourceTask.getCandidateUsers(),
                sourceTask.getCandidateGroups(),
                sourceConfig);
    }

    /**
     * DOM 发布校验与 Flowable 运行时共用的分配模式投影。
     *
     * @param currentMultiInstance 当前多实例，供本方法处理分配模式时使用
     * @param sourceMultiInstance 来源多实例，供本方法处理分配模式时使用
     * @param sourceAssignee 来源办理人，供本方法处理分配模式时使用
     * @param sourceCandidateUsers 来源候选人用户集合，供本方法处理分配模式时使用
     * @param sourceCandidateGroups 来源候选人分组集合，供本方法处理分配模式时使用
     * @param sourceConfig 来源配置内容，决定后续分配模式的处理规则
     * @return 处理后的分配模式文本，供调用方比较或展示
     */
    public static String assignmentMode(
            boolean currentMultiInstance,
            boolean sourceMultiInstance,
            String sourceAssignee,
            Collection<String> sourceCandidateUsers,
            Collection<String> sourceCandidateGroups,
            Map<String, Object> sourceConfig) {
        if (currentMultiInstance) {
            return "MULTI_INSTANCE";
        }
        LegacyMultiInstanceAssignmentParser.LegacyAssignment legacy =
                LegacyMultiInstanceAssignmentParser.parse(
                        sourceConfig == null ? Map.of() : sourceConfig);
        if (LegacyMultiInstanceAssignmentParser
                .usesLegacyMultiInstanceAssignment(
                        sourceConfig, sourceMultiInstance)) {
            return legacy.userKeys().isEmpty()
                    && (!legacy.groupKeys().isEmpty()
                    || !legacy.roleKeys().isEmpty())
                    ? "CANDIDATE" : "DIRECT";
        }
        if (literalAssignee(sourceAssignee)) {
            return "DIRECT";
        }
        String configured = firstText(
                sourceConfig == null
                        ? null : sourceConfig.get("assignmentMode"),
                sourceConfig == null ? null : sourceConfig.get("mode"));
        if (StringUtils.hasText(configured)) {
            String normalized = configured.trim()
                    .toUpperCase(Locale.ROOT);
            if ("DIRECT".equals(normalized)
                    || "CANDIDATE".equals(normalized)) {
                return normalized;
            }
            if (!"MULTI_INSTANCE".equals(normalized)) {
                throw new IllegalArgumentException(
                        "不支持的审批分配模式: " + configured);
            }
        }
        if (hasValues(sourceCandidateUsers)
                || hasValues(sourceCandidateGroups)) {
            return "CANDIDATE";
        }
        String assigneeType = text(sourceConfig == null
                ? null : sourceConfig.get("assigneeType"));
        if (StringUtils.hasText(assigneeType)) {
            String normalizedType = assigneeType.trim()
                    .toLowerCase(Locale.ROOT);
            if ("candidate".equals(normalizedType)
                    || "role".equals(normalizedType)
                    || "group".equals(normalizedType)) {
                return "CANDIDATE";
            }
        }
        return "DIRECT";
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
            throw invalid(userTask,
                    propertyName + " 不是合法 JSON", exception);
        }
    }

    /**
     * 创建输入数据的不可变副本，避免调用方后续修改影响请求内容。
     *
     * @param value 待处理不可变副本的原始输入，结果供调用方继续使用
     * @return 输入数据的不可变副本；输入为空时为空映射
     */
    private Map<String, Object> immutableCopy(
            Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    /**
     * 判断是否包含表达式；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否包含表达式的原始输入，结果供调用方继续使用
     * @return 表达式条件成立时为 true，否则为 false
     */
    private boolean containsExpression(String value) {
        return value.contains("${") || value.contains("#{");
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param task 任务，供本方法处理无效时使用
     * @param message 消息，供本方法处理无效时使用
     * @return 处理后的无效结果，供调用方继续处理
     */
    private IllegalArgumentException invalid(
            UserTask task,
            String message) {
        return invalid(task, message, null);
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param task 任务，供本方法处理无效时使用
     * @param message 消息，供本方法处理无效时使用
     * @param cause 原因，供本方法处理无效时使用
     * @return 处理后的无效结果，供调用方继续处理
     */
    private IllegalArgumentException invalid(
            UserTask task,
            String message,
            Throwable cause) {
        String nodeId = task == null ? null : task.getId();
        String detail = "节点 " + (nodeId == null ? "<unknown>" : nodeId)
                + " 的审批人引用无效: " + message;
        return cause == null
                ? new IllegalArgumentException(detail)
                : new IllegalArgumentException(detail, cause);
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private static String firstText(Object... values) {
        for (Object value : values) {
            String result = text(value);
            if (StringUtils.hasText(result)) {
                return result.trim();
            }
        }
        return null;
    }

    /**
     * 判断字面值办理人条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理字面值办理人的原始输入，结果供调用方继续使用
     * @return 字面值办理人条件成立时为 true，否则为 false
     */
    private static boolean literalAssignee(String value) {
        return StringUtils.hasText(value)
                && !value.contains("${")
                && !value.contains("#{");
    }

    /**
     * 判断是否具有值集合；判断结果决定调用方的后续分支。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 值集合条件成立时为 true，否则为 false
     */
    private static boolean hasValues(Collection<String> values) {
        return values != null
                && values.stream().anyMatch(StringUtils::hasText);
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 引用解析结果；chainNodeIds 从当前节点起并包含终端规则节点。
     *
     * @param sourceTask 来源任务，保存在对象中供后续校验、查询或展示
     * @param assigneeConfig 办理人配置内容，决定后续已解析分配的处理规则
     * @param chainNodeIds 链节点ID 集合，保存在对象中供后续校验、查询或展示
     */
    public record ResolvedAssignment(
            UserTask sourceTask,
            Map<String, Object> assigneeConfig,
            List<String> chainNodeIds) {

        /**
         * 判断已引用条件是否成立，供调用方选择后续分支。
         *
         * @return 已引用条件成立时为 true，否则为 false
         */
        public boolean referenced() {
            return chainNodeIds != null && chainNodeIds.size() > 1;
        }
    }
}
