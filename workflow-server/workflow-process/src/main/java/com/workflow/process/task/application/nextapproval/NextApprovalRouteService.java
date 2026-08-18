package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.entity.form.application.FormSubmissionPreviewDeferredException;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.application.MultiInstanceOutcomeService;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基于任务绑定的已部署 BPMN 模型预测下一人工审批节点。
 */
@Service
@RequiredArgsConstructor
public class NextApprovalRouteService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final FlowableConditionEvaluator conditionEvaluator;
    private final NextApproverSelectionPolicyReader policyReader;
    private final NodeFormSubmissionService nodeFormSubmissionService;
    private final ObjectMapper objectMapper;
    private final MultiInstanceOutcomeService multiInstanceOutcomeService;

    public NextApprovalResolution resolve(
            String taskId,
            NextApprovalPreviewRequest request) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            throw new IllegalArgumentException(
                    "任务不存在或已处理: " + taskId);
        }
        return resolve(task, request, true);
    }

    /**
     * 提交前重算入口。includeSubmittedForm=false 时只使用 applyEditableData 后的
     * 引擎变量，避免再次信任请求中的原始表单数据。
     */
    public NextApprovalResolution resolve(
            Task task,
            NextApprovalPreviewRequest request,
            boolean includeSubmittedForm) {
        String processDefinitionId = task.getProcessDefinitionId();
        BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
        if (model == null || model.getMainProcess() == null) {
            return result(
                    task,
                    NextApprovalPreviewStatus.BLOCKED,
                    "无法读取任务对应的已部署流程模型",
                    null,
                    List.of(),
                    Map.of());
        }
        FlowElement current = model.getMainProcess().getFlowElement(
                task.getTaskDefinitionKey(), true);
        if (!(current instanceof UserTask currentTask)) {
            return result(
                    task,
                    NextApprovalPreviewStatus.BLOCKED,
                    "当前任务节点不在已部署流程模型中",
                    null,
                    List.of(),
                    Map.of());
        }
        // This capability is opt-in. Legacy deployments without any visible
        // selection policy must keep completing even when their historical
        // conditions cannot be safely pre-executed.
        try {
            if (!hasVisibleSelection(
                    processDefinitionId,
                    currentTask,
                    model)) {
                return result(
                        task,
                        NextApprovalPreviewStatus.READY,
                        null,
                        null,
                        List.of(),
                        Map.of());
            }
        } catch (IllegalArgumentException exception) {
            return result(
                    task,
                    NextApprovalPreviewStatus.BLOCKED,
                    exception.getMessage(),
                    null,
                    List.of(),
                    Map.of());
        }
        if (currentTask.isAsynchronous()
                || currentTask.isAsynchronousLeave()) {
            return result(
                    task,
                    NextApprovalPreviewStatus.DEFERRED,
                    "当前任务异步离开，需等待引擎运行后确定下一节点",
                    null,
                    List.of(),
                    Map.of());
        }
        if (currentTask.hasMultiInstanceLoopCharacteristics()
                && !multiInstanceOutcomeService.willFinishCurrentNode(
                task,
                request == null ? "approve" : request.getAction())) {
            return result(
                    task,
                    NextApprovalPreviewStatus.DEFERRED,
                    "当前任务为多实例审批，需等待本节点汇聚后确定下一节点",
                    null,
                    List.of(),
                    Map.of());
        }

        Map<String, Object> variables = new LinkedHashMap<>(
                runtimeService.getVariables(task.getProcessInstanceId()));
        if (includeSubmittedForm && request != null) {
            final Map<String, Object> editable;
            try {
                editable = nodeFormSubmissionService.projectEditableData(
                        task, request.getFormData());
            } catch (FormSubmissionPreviewDeferredException exception) {
                return result(
                        task,
                        NextApprovalPreviewStatus.DEFERRED,
                        exception.getMessage(),
                        null,
                        List.of(),
                        variables);
            } catch (RuntimeException exception) {
                return result(
                        task,
                        NextApprovalPreviewStatus.BLOCKED,
                        "表单提交前处理预览失败: "
                                + safeMessage(exception),
                        null,
                        List.of(),
                        variables);
            }
            variables.putAll(editable);
            Map<String, Object> entityData = mapValue(
                    variables.get("entityData"));
            if (!entityData.isEmpty() || !editable.isEmpty()) {
                Map<String, Object> projectedEntity =
                        new LinkedHashMap<>(entityData);
                projectedEntity.putAll(editable);
                variables.put("entityData", projectedEntity);
            }
        }
        applyActionVariables(variables, request);
        if ("transfer".equals(variables.get("action"))) {
            return result(
                    task,
                    NextApprovalPreviewStatus.DEFERRED,
                    "转办不会推进到下一流程节点",
                    null,
                    List.of(),
                    variables);
        }

        Traversal traversal = new Traversal();
        try {
            for (SequenceFlow flow : selectOutgoing(
                    currentTask,
                    currentTask.getOutgoingFlows(),
                    variables)) {
                walk(
                        target(flow, model),
                        model,
                        variables,
                        new HashSet<>(Set.of(currentTask.getId())),
                        traversal);
            }
            if (currentTask.getOutgoingFlows() == null
                    || currentTask.getOutgoingFlows().isEmpty()) {
                traversal.block("当前任务没有后续连线");
            } else if (!traversal.reachedAnything()) {
                traversal.block("没有命中可执行的后续路径");
            }
        } catch (FlowableConditionEvaluator.UnsafePreviewExpressionException exception) {
            traversal.defer("流程条件不适合安全预执行: "
                    + safeMessage(exception));
        } catch (Exception exception) {
            traversal.block("流程条件计算失败: " + safeMessage(exception));
        }

        if (traversal.blockedMessage != null) {
            return result(
                    task,
                    NextApprovalPreviewStatus.BLOCKED,
                    traversal.blockedMessage,
                    null,
                    List.of(),
                    variables);
        }
        if (traversal.deferredMessage != null) {
            return result(
                    task,
                    NextApprovalPreviewStatus.DEFERRED,
                    traversal.deferredMessage,
                    null,
                    List.of(),
                    variables);
        }

        List<NextApprovalTarget> targets = new ArrayList<>();
        try {
            traversal.userTasks.values().forEach(userTask ->
                    targets.add(policyReader.read(
                            processDefinitionId, userTask, model)));
        } catch (IllegalArgumentException exception) {
            return result(
                    task,
                    NextApprovalPreviewStatus.BLOCKED,
                    exception.getMessage(),
                    null,
                    List.of(),
                    variables);
        }
        if (targets.stream().anyMatch(this::hasUnpredictableAssignment)) {
            return result(
                    task,
                    NextApprovalPreviewStatus.DEFERRED,
                    "下一用户任务使用动态办理人表达式，需等待引擎运行后确定",
                    null,
                    List.of(),
                    variables);
        }
        String scopeKey = groupScopeKey(
                processDefinitionId,
                currentTask.getId(),
                targets);
        return result(
                task,
                NextApprovalPreviewStatus.READY,
                null,
                scopeKey,
                targets,
                variables);
    }

    private boolean hasUnpredictableAssignment(
            NextApprovalTarget target) {
        if (!target.selectionPolicy().visible()
                || "MULTI_INSTANCE".equals(
                target.selectionPolicy().assignmentMode())) {
            return false;
        }
        UserTask userTask = target.assignmentSourceTask();
        if (dynamicExpression(userTask.getAssignee())) {
            return true;
        }
        return (userTask.getCandidateUsers() != null
                && userTask.getCandidateUsers().stream()
                .anyMatch(this::dynamicExpression))
                || (userTask.getCandidateGroups() != null
                && userTask.getCandidateGroups().stream()
                .anyMatch(this::dynamicExpression));
    }

    private boolean dynamicExpression(String value) {
        return StringUtils.hasText(value)
                && (value.contains("${") || value.contains("#{"));
    }

    private void walk(
            FlowElement element,
            BpmnModel model,
            Map<String, Object> variables,
            Set<String> path,
            Traversal traversal) {
        if (element == null) {
            traversal.block("流程连线指向不存在的节点");
            return;
        }
        if (!path.add(element.getId())) {
            traversal.defer("后续路径包含循环，需等待引擎运行后确定");
            return;
        }
        traversal.reached = true;
        if (element instanceof UserTask userTask) {
            if (userTask.isAsynchronous()
                    || userTask.isAsynchronousLeave()) {
                traversal.defer("下一用户任务为异步节点，需等待引擎运行后确定");
                return;
            }
            if (maySkipAutomatically(userTask)) {
                traversal.defer(
                        "下一用户任务可能被自动跳过，需等待引擎运行后确定: "
                                + displayName(userTask));
                return;
            }
            traversal.userTasks.putIfAbsent(userTask.getId(), userTask);
            return;
        }
        if (element instanceof EndEvent) {
            traversal.ended = true;
            return;
        }
        if (!(element instanceof Gateway gateway)) {
            traversal.defer("后续包含自动、等待、调用或子流程节点: "
                    + displayName(element));
            return;
        }
        if (!(gateway instanceof ExclusiveGateway)
                && !(gateway instanceof InclusiveGateway)
                && !(gateway instanceof ParallelGateway)) {
            traversal.defer("后续包含不支持预判的网关: "
                    + displayName(gateway));
            return;
        }
        if (gateway.isAsynchronous()
                || gateway.isAsynchronousLeave()) {
            traversal.defer("后续网关为异步节点: " + displayName(gateway));
            return;
        }
        if (gateway.getIncomingFlows() != null
                && gateway.getIncomingFlows().size() > 1) {
            traversal.defer("后续包含汇聚网关: " + displayName(gateway));
            return;
        }
        List<SequenceFlow> selected = selectOutgoing(
                gateway,
                gateway.getOutgoingFlows(),
                variables);
        if (selected.isEmpty()) {
            traversal.block("网关没有命中后续路径: " + displayName(gateway));
            return;
        }
        for (SequenceFlow flow : selected) {
            walk(
                    target(flow, model),
                    model,
                    variables,
                    new HashSet<>(path),
                    traversal);
        }
    }

    private boolean maySkipAutomatically(UserTask userTask) {
        String skipExpression = firstText(
                userTask.getSkipExpression(),
                userTask.getAttributeValue(
                        "http://flowable.org/bpmn",
                        "skipExpression"),
                userTask.getAttributeValue("", "skipExpression"),
                ConfiguredTaskPropertyReader.read(
                        userTask, "skipExpression"));
        if (StringUtils.hasText(skipExpression)) {
            return true;
        }
        return Boolean.parseBoolean(firstText(
                ConfiguredTaskPropertyReader.read(userTask, "skipNode"),
                "false"));
    }

    private boolean hasVisibleSelection(
            String processDefinitionId,
            UserTask currentTask,
            BpmnModel model) {
        if (currentTask.getOutgoingFlows() == null) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        visited.add(currentTask.getId());
        for (SequenceFlow flow : currentTask.getOutgoingFlows()) {
            if (hasVisibleSelectionDownstream(
                    processDefinitionId,
                    target(flow, model),
                    model,
                    visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 仅检查当前节点之后、每条路径遇到的第一批用户任务。目标节点的
     * 配置属于它的前序审批面板；不能因为当前节点自身或无关分支有配置
     * 就开启预测并阻断旧流程。
     */
    private boolean hasVisibleSelectionDownstream(
            String processDefinitionId,
            FlowElement element,
            BpmnModel model,
            Set<String> visited) {
        if (element == null || !visited.add(element.getId())) {
            return false;
        }
        if (element instanceof UserTask userTask) {
            return policyReader.read(
                    processDefinitionId, userTask, model)
                    .selectionPolicy().visible();
        }
        if (!(element instanceof FlowNode flowNode)
                || flowNode.getOutgoingFlows() == null) {
            return false;
        }
        for (SequenceFlow flow : flowNode.getOutgoingFlows()) {
            if (hasVisibleSelectionDownstream(
                    processDefinitionId,
                    target(flow, model),
                    model,
                    new HashSet<>(visited))) {
                return true;
            }
        }
        return false;
    }

    private List<SequenceFlow> selectOutgoing(
            FlowNode node,
            List<SequenceFlow> outgoing,
            Map<String, Object> variables) {
        if (outgoing == null || outgoing.isEmpty()) {
            return List.of();
        }
        if (node instanceof ParallelGateway) {
            return List.copyOf(outgoing);
        }
        String defaultFlow = node instanceof Gateway gateway
                ? gateway.getDefaultFlow()
                : node instanceof Activity activity
                        ? activity.getDefaultFlow()
                        : null;
        List<SequenceFlow> matched = new ArrayList<>();
        for (SequenceFlow flow : outgoing) {
            if (flow.getId().equals(defaultFlow)) {
                continue;
            }
            boolean accepted = !StringUtils.hasText(
                    flow.getConditionExpression())
                    || conditionEvaluator.evaluate(
                            flow.getConditionExpression(), variables);
            if (accepted) {
                matched.add(flow);
                if (node instanceof ExclusiveGateway) {
                    return List.of(flow);
                }
            }
        }
        if (!matched.isEmpty()) {
            return matched;
        }
        if (StringUtils.hasText(defaultFlow)) {
            return outgoing.stream()
                    .filter(flow -> defaultFlow.equals(flow.getId()))
                    .findFirst()
                    .map(List::of)
                    .orElse(List.of());
        }
        return List.of();
    }

    private FlowElement target(SequenceFlow flow, BpmnModel model) {
        return flow.getTargetFlowElement() != null
                ? flow.getTargetFlowElement()
                : model.getMainProcess().getFlowElement(
                        flow.getTargetRef(), true);
    }

    private void applyActionVariables(
            Map<String, Object> variables,
            NextApprovalPreviewRequest request) {
        String action = normalizeAction(request == null
                ? null : request.getAction());
        variables.put("approved", action);
        variables.put("action", action);
        variables.put(
                "comment",
                request == null || request.getComment() == null
                        ? ""
                        : request.getComment());
        if (request != null) {
            if (StringUtils.hasText(request.getActionLabel())) {
                variables.put("actionLabel", request.getActionLabel());
            }
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "approve";
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> "approve";
            case "REJECT", "REJECTED" -> "reject";
            case "TRANSFER", "TRANSFERRED" -> "transfer";
            default -> action;
        };
    }

    private String groupScopeKey(
            String processDefinitionId,
            String currentNodeId,
            List<NextApprovalTarget> targets) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("processDefinitionId", processDefinitionId);
        canonical.put("currentNodeId", currentNodeId);
        canonical.put("targets", targets.stream()
                .sorted(Comparator.comparing(target ->
                        target.userTask().getId()))
                .map(target -> Map.of(
                        "nodeId", target.userTask().getId(),
                        "policyScopeKey", target.selectionPolicy().scopeKey()
                                == null ? "" : target.selectionPolicy().scopeKey()))
                .toList());
        try {
            byte[] json = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception exception) {
            throw new IllegalStateException("下一审批路径签名失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String displayName(FlowElement element) {
        return StringUtils.hasText(element.getName())
                ? element.getName() + "(" + element.getId() + ")"
                : element.getId();
    }

    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private NextApprovalResolution result(
            Task task,
            NextApprovalPreviewStatus status,
            String message,
            String scopeKey,
            List<NextApprovalTarget> targets,
            Map<String, Object> variables) {
        return new NextApprovalResolution(
                task,
                status,
                message,
                scopeKey,
                List.copyOf(targets),
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(variables)));
    }

    private static final class Traversal {
        private final Map<String, UserTask> userTasks =
                new LinkedHashMap<>();
        private boolean reached;
        private boolean ended;
        private String deferredMessage;
        private String blockedMessage;

        private void defer(String message) {
            if (deferredMessage == null) {
                deferredMessage = message;
            }
        }

        private void block(String message) {
            if (blockedMessage == null) {
                blockedMessage = message;
            }
        }

        private boolean reachedAnything() {
            return reached || ended || !userTasks.isEmpty();
        }
    }
}
