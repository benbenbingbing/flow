package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.entity.form.application.FormSubmissionPreviewDeferredException;
import com.workflow.process.assignment.entity.EntityUserReferenceFieldConfig;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
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

    /**
     * 解析下一步审批路由；输出作为后续校验或处理的输入。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param request 本次请求，后续经校验后用于解析下一步审批路由
     * @return 解析后的下一步审批路由结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
     *
     * @param task 任务，作为 {@code result} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于解析下一步审批路由
     * @param includeSubmittedForm {@code include}已提交表单，供本方法解析下一步审批路由时使用
     * @return 解析后的下一步审批路由结果，供调用方继续处理
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
        Set<String> submittedEditableFields = Set.of();
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
            submittedEditableFields = Set.copyOf(editable.keySet());
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
        Set<String> projectedSubmittedFields = submittedEditableFields;
        boolean submittedFieldAffectsAssignee =
                !projectedSubmittedFields.isEmpty()
                        && targets.stream().anyMatch(target ->
                        readsSubmittedEntityUserField(
                                target, projectedSubmittedFields));
        if (submittedFieldAffectsAssignee) {
            // 实体解析器只信任持久化记录。当前表单尚未落库时不能展示旧人，
            // 正式提交保存后由任务监听器/collection handler 重新权威解析。
            return result(
                    task,
                    NextApprovalPreviewStatus.DEFERRED,
                    "当前提交会更新下一节点审批人字段，保存后由流程重新解析",
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

    /**
     * 判断是否具有{@code unpredictable}分配；判断结果决定调用方的后续分支。
     *
     * @param target 目标，供本方法判断是否具有{@code unpredictable}分配时使用
     * @return {@code unpredictable}分配条件成立时为 true，否则为 false
     */
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

    /**
     * 判断{@code reads}已提交实体用户字段条件是否成立，供调用方选择后续分支。
     *
     * @param target 目标，作为 {@code effectiveResolver} 的输入影响后续处理
     * @param submittedEditableFields 已提交可编辑字段，供本方法处理{@code reads}已提交实体用户字段时使用
     * @return {@code reads}已提交实体用户字段条件成立时为 true，否则为 false
     */
    private boolean readsSubmittedEntityUserField(
            NextApprovalTarget target,
            Set<String> submittedEditableFields) {
        Map<String, Object> config = target.assigneeConfig();
        var effectiveResolver = LegacyMultiInstanceAssignmentParser
                .effectiveResolver(
                        config,
                        target.assignmentSourceTask()
                                .hasMultiInstanceLoopCharacteristics());
        if (readsSubmittedEntityUserField(
                effectiveResolver.resolverCode(),
                effectiveResolver.extraParams(),
                submittedEditableFields)) {
            return true;
        }
        NextApproverSelectionPolicy policy = target.selectionPolicy();
        return policy.sourceType()
                == NextApproverSelectionPolicy.SourceType.RESOLVER
                && readsSubmittedEntityUserField(
                policy.resolverCode(),
                policy.extraParams(),
                submittedEditableFields);
    }

    /**
     * 判断一个实体用户字段解析器是否读取本次尚未落库的字段。
     *
     * @param resolverCode 解析器编码，后续用于处理{@code reads}已提交实体用户字段时定位或关联目标
     * @param extraParams 附加参数，供本方法处理{@code reads}已提交实体用户字段时使用
     * @param submittedEditableFields 已提交可编辑字段，供本方法处理{@code reads}已提交实体用户字段时使用
     * @return {@code reads}已提交实体用户字段条件成立时为 true，否则为 false
     */
    private boolean readsSubmittedEntityUserField(
            String resolverCode,
            Map<String, Object> extraParams,
            Set<String> submittedEditableFields) {
        if (!EntityUserReferenceFieldConfig.RESOLVER_CODE.equals(
                resolverCode)) {
            return false;
        }
        String fieldCode = String.valueOf(extraParams.getOrDefault(
                "fieldCode", "")).trim();
        return StringUtils.hasText(fieldCode)
                && submittedEditableFields.stream().anyMatch(value ->
                sameFieldCode(value, fieldCode));
    }

    /**
     * 判断相同字段编码条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同字段编码时使用
     * @param right 右侧，作为 {@code left.equals} 的输入影响后续处理
     * @return 相同字段编码条件成立时为 true，否则为 false
     */
    private boolean sameFieldCode(String left, String right) {
        return left.equals(right)
                || left.replace("_", "").equalsIgnoreCase(
                right.replace("_", ""));
    }

    /**
     * 判断动态表达式条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理动态表达式的原始输入，结果供调用方继续使用
     * @return 动态表达式条件成立时为 true，否则为 false
     */
    private boolean dynamicExpression(String value) {
        return StringUtils.hasText(value)
                && (value.contains("${") || value.contains("#{"));
    }

    /**
     * 处理{@code walk}，并将结果传给后续步骤。
     *
     * @param element 元素，作为 {@code traversal.defer} 的输入影响后续处理
     * @param model 模型，供本方法处理{@code walk}时使用
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param path 路径，供本方法处理{@code walk}时使用
     * @param traversal 遍历，供本方法处理{@code walk}时使用
     */
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

    /**
     * 判断{@code may}跳过{@code automatically}条件是否成立，供调用方选择后续分支。
     *
     * @param userTask 用户任务，作为 {@code firstText} 的输入影响后续处理
     * @return {@code may}跳过{@code automatically}条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否具有可见选择；判断结果决定调用方的后续分支。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param currentTask 当前任务，写入当前任务信息供后续待办展示和状态同步
     * @param model 模型，供本方法判断是否具有可见选择时使用
     * @return 可见选择条件成立时为 true，否则为 false
     */
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
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param element 元素，供本方法判断是否具有可见选择{@code downstream}时使用
     * @param model 模型，作为 {@code policyReader.read} 的输入影响后续处理
     * @param visited {@code visited}，供本方法判断是否具有可见选择{@code downstream}时使用
     * @return 可见选择{@code downstream}条件成立时为 true，否则为 false
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

    /**
     * 查询{@code outgoing}；查询结果供调用方展示或继续处理。
     *
     * @param node 节点，供本方法查询{@code outgoing}时使用
     * @param outgoing {@code outgoing}，作为 {@code List.copyOf} 的输入影响后续处理
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @return 序列流程集合，供调用方遍历或展示
     */
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

    /**
     * 处理目标，并将结果传给后续步骤。
     *
     * @param flow 流程，供本方法处理目标时使用
     * @param model 模型，供本方法处理目标时使用
     * @return 处理后的目标结果，供调用方继续处理
     */
    private FlowElement target(SequenceFlow flow, BpmnModel model) {
        return flow.getTargetFlowElement() != null
                ? flow.getTargetFlowElement()
                : model.getMainProcess().getFlowElement(
                        flow.getTargetRef(), true);
    }

    /**
     * 应用动作流程变量，并将结果传给后续步骤。
     *
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param request 本次请求，后续经校验后用于应用动作流程变量
     */
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

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 规范化动作；输出作为后续校验或处理的输入。
     *
     * @param action 动作标识，决定后续动作采用的处理分支
     * @return 规范化后的动作文本，供调用方比较或展示
     */
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

    /**
     * 生成分组作用域键文本，供后续匹配或展示。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param currentNodeId 当前节点ID，后续用于处理分组作用域键时定位或关联目标
     * @param targets 目标集合，作为 {@code canonical.put} 的输入影响后续处理
     * @return 处理后的分组作用域键文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 生成展示名称文本，供后续匹配或展示。
     *
     * @param element 元素，供本方法处理展示名称时使用
     * @return 处理后的展示名称文本，供调用方比较或展示
     */
    private String displayName(FlowElement element) {
        return StringUtils.hasText(element.getName())
                ? element.getName() + "(" + element.getId() + ")"
                : element.getId();
    }

    /**
     * 生成安全消息文本，供后续匹配或展示。
     *
     * @param exception 异常，供本方法处理安全消息时使用
     * @return 处理后的安全消息文本，供调用方比较或展示
     */
    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    /**
     * 处理结果，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code NextApprovalResolution} 的输入影响后续处理
     * @param status 状态标识，决定后续结果采用的处理分支
     * @param message 消息，作为 {@code NextApprovalResolution} 的输入影响后续处理
     * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
     * @param targets 目标集合，作为 {@code NextApprovalResolution} 的输入影响后续处理
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @return 处理后的结果，供调用方继续处理
     */
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

    /**
     * 负责遍历的业务处理；协调校验、状态变化及后续结果传递。
     */
    private static final class Traversal {
        private final Map<String, UserTask> userTasks =
                new LinkedHashMap<>();
        private boolean reached;
        private boolean ended;
        private String deferredMessage;
        private String blockedMessage;

        /**
         * 处理{@code defer}，并将结果传给后续步骤。
         *
         * @param message 消息，供本方法处理{@code defer}时使用
         */
        private void defer(String message) {
            if (deferredMessage == null) {
                deferredMessage = message;
            }
        }

        /**
         * 处理{@code block}，并将结果传给后续步骤。
         *
         * @param message 消息，供本方法处理{@code block}时使用
         */
        private void block(String message) {
            if (blockedMessage == null) {
                blockedMessage = message;
            }
        }

        /**
         * 判断{@code reached}{@code anything}条件是否成立，供调用方选择后续分支。
         *
         * @return {@code reached}{@code anything}条件成立时为 true，否则为 false
         */
        private boolean reachedAnything() {
            return reached || ended || !userTasks.isEmpty();
        }
    }
}
