package com.workflow.process.task.application.operation;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 节点操作的唯一后端决策入口。
 *
 * <p>所有判断均从任务所属部署版本读取策略。控制器只能调用本服务做强制校验，
 * 前端返回的可用动作仅用于展示，不能作为授权依据。</p>
 */
@Service
@RequiredArgsConstructor
public class NodeOperationDecisionService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final NodeOperationPolicyParser policyParser;
    private final NodeOperationConditionEvaluator conditionEvaluator;

    /** 返回当前用户在任务上的全部标准操作决策。 */
    public Map<String, ActionDecision> availableActions(String taskId) {
        TaskSnapshot snapshot = snapshot(taskId);
        LinkedHashMap<String, ActionDecision> decisions = new LinkedHashMap<>();
        for (NodeOperationPolicy.Operation operation : NodeOperationPolicy.Operation.values()) {
            decisions.put(operation.apiCode(), decide(snapshot, operation, CheckContext.availability()));
        }
        return decisions;
    }

    /**
     * 强制校验单个任务操作。拒绝原因统一抛出 ForbiddenException，供全局异常处理返回 403。
     */
    public void requireAllowed(
            String taskId,
            NodeOperationPolicy.Operation operation,
            CheckContext context) {
        ActionDecision decision = decide(snapshot(taskId), operation,
                context == null ? CheckContext.request() : context.withRequestDetails(true));
        if (!decision.allowed()) {
            throw new ForbiddenException(decision.message());
        }
    }

    /**
     * 对流程级动作采用“所有活动节点均允许”的收敛策略，避免并行节点中任一受限分支被绕过。
     */
    public void requireAllowedForProcess(
            String processInstanceId,
            NodeOperationPolicy.Operation operation,
            CheckContext context) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();
        if (tasks == null || tasks.isEmpty()) {
            throw new ForbiddenException("当前流程没有可校验的活动任务");
        }
        for (Task task : tasks) {
            ActionDecision decision = decide(snapshot(task), operation,
                    context == null ? CheckContext.request() : context.withRequestDetails(true));
            if (!decision.allowed()) {
                throw new ForbiddenException(decision.message());
            }
        }
    }

    /**
     * 设计器模拟身份预览，不访问运行时任务，也不会产生任何流程副作用。
     */
    public ActionDecision simulate(
            String policyJson,
            NodeOperationPolicy.Operation operation,
            SimulationContext context) {
        NodeOperationPolicy policy = policyParser.parse(policyJson);
        SimulationContext effective = context == null ? SimulationContext.empty() : context;
        Map<String, Object> variables = effective.variables() == null
                ? Map.of()
                : Map.copyOf(effective.variables());
        TaskSnapshot snapshot = new TaskSnapshot(
                null,
                policy,
                variables,
                effective.processStartedAt());
        CheckContext checkContext = new CheckContext(
                effective.reason(),
                effective.targetUserIds(),
                effective.targetNodeId(),
                effective.addSignType(),
                effective.requestVariables(),
                effective.permissions(),
                effective.currentUserId(),
                effective.now(),
                true);
        return decide(snapshot, operation, checkContext);
    }

    /**
     * 根据矩阵自动生成测试中心可执行的正反向覆盖模板。
     */
    public List<CoverageCase> generateCoverage(String policyJson) {
        NodeOperationPolicy policy = policyParser.parse(policyJson);
        List<CoverageCase> cases = new ArrayList<>();
        for (NodeOperationPolicy.Operation operation : NodeOperationPolicy.Operation.values()) {
            NodeOperationPolicy.Rule rule = policy.rule(operation);
            cases.add(new CoverageCase(operation.apiCode(), "BASELINE",
                    rule.enabled() ? "ALLOW" : "DENY", "验证启用状态和条件成立路径"));
            if (StringUtils.hasText(rule.permissionCode())) {
                cases.add(new CoverageCase(operation.apiCode(), "PERMISSION_MISSING",
                        "DENY", "移除权限码 " + rule.permissionCode()));
            }
            if (rule.reasonRequired()) {
                cases.add(new CoverageCase(operation.apiCode(), "REASON_MISSING",
                        "DENY", "提交空理由"));
            }
            if (StringUtils.hasText(rule.conditionExpression())) {
                cases.add(new CoverageCase(operation.apiCode(), "CONDITION_FALSE",
                        "DENY", "构造条件不成立的业务变量"));
            }
            if (rule.targetScope() == NodeOperationPolicy.TargetScope.FIXED
                    || !rule.allowedRejectTargets().isEmpty()) {
                cases.add(new CoverageCase(operation.apiCode(), "TARGET_OUT_OF_SCOPE",
                        "DENY", "选择白名单之外的目标"));
            }
            if (rule.withdrawWithinMinutes() != null) {
                cases.add(new CoverageCase(operation.apiCode(), "WITHDRAW_EXPIRED",
                        "DENY", "流程开始时间超过撤回时限"));
            }
        }
        return List.copyOf(cases);
    }

    private ActionDecision decide(
            TaskSnapshot snapshot,
            NodeOperationPolicy.Operation operation,
            CheckContext context) {
        NodeOperationPolicy.Rule rule = snapshot.policy().rule(operation);
        if (!rule.enabled()) {
            return denied(operation, "DISABLED", "该节点未开放此操作", rule);
        }
        if (StringUtils.hasText(rule.permissionCode())
                && !hasPermission(rule.permissionCode(), context.permissions())) {
            return denied(operation, "PERMISSION_DENIED",
                    "缺少操作权限: " + rule.permissionCode(), rule);
        }

        Map<String, Object> conditionVariables = conditionVariables(snapshot, context);
        try {
            if (!conditionEvaluator.compile(
                    rule.conditionExpression(), snapshot.policy().allowedVariables())
                    .evaluate(conditionVariables)) {
                return denied(operation, "CONDITION_FALSE", "操作适用条件不成立", rule);
            }
        } catch (RuntimeException exception) {
            return denied(operation, "CONDITION_ERROR",
                    "操作条件计算失败: " + exception.getMessage(), rule);
        }

        if (operation == NodeOperationPolicy.Operation.WITHDRAW
                && rule.withdrawWithinMinutes() != null) {
            Instant start = snapshot.processStartedAt();
            Instant now = context.now() == null ? Instant.now() : context.now();
            if (start == null) {
                return denied(operation, "START_TIME_UNAVAILABLE", "无法确认流程开始时间", rule);
            }
            if (Duration.between(start, now).toMinutes() >= rule.withdrawWithinMinutes()) {
                return denied(operation, "WITHDRAW_EXPIRED", "已超过允许撤回的时间窗口", rule);
            }
        }

        if (context.enforceRequestDetails()) {
            ActionDecision requestDecision = validateRequestDetails(operation, rule, context);
            if (requestDecision != null) {
                return requestDecision;
            }
        }
        return new ActionDecision(operation.apiCode(), true, "ALLOWED", "允许操作", rule);
    }

    private ActionDecision validateRequestDetails(
            NodeOperationPolicy.Operation operation,
            NodeOperationPolicy.Rule rule,
            CheckContext context) {
        if (rule.reasonRequired() && !StringUtils.hasText(context.reason())) {
            return denied(operation, "REASON_REQUIRED", "该操作必须填写理由", rule);
        }
        if (rule.reasonTemplateRequired()
                && (!StringUtils.hasText(context.reason())
                || !rule.reasonTemplates().contains(context.reason().trim()))) {
            return denied(operation, "REASON_TEMPLATE_REQUIRED", "必须选择预设理由模板", rule);
        }

        if (rule.targetScope() == NodeOperationPolicy.TargetScope.FIXED
                && needsUserTarget(operation)) {
            Set<String> selected = context.targetUserIds() == null
                    ? Set.of()
                    : Set.copyOf(context.targetUserIds());
            if (selected.isEmpty() || !rule.targetIds().containsAll(selected)) {
                return denied(operation, "TARGET_OUT_OF_SCOPE", "所选办理人不在允许目标范围内", rule);
            }
        }
        if (operation == NodeOperationPolicy.Operation.REJECT
                && !rule.allowedRejectTargets().isEmpty()
                && (!StringUtils.hasText(context.targetNodeId())
                || !rule.allowedRejectTargets().contains(context.targetNodeId()))) {
            return denied(operation, "REJECT_TARGET_DENIED", "驳回目标节点不在允许范围内", rule);
        }
        if (operation.isAddSign() && !rule.allowedAddSignTypes().isEmpty()) {
            String type = StringUtils.hasText(context.addSignType())
                    ? context.addSignType().trim().toUpperCase(Locale.ROOT)
                    : addSignTypeOf(operation);
            if (!rule.allowedAddSignTypes().contains(type)) {
                return denied(operation, "ADD_SIGN_TYPE_DENIED", "当前节点不允许该加签类型", rule);
            }
        }
        return null;
    }

    private TaskSnapshot snapshot(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return snapshot(task);
    }

    private TaskSnapshot snapshot(Task task) {
        BpmnModel model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        FlowElement element = findElement(model, task.getTaskDefinitionKey());
        if (!(element instanceof UserTask userTask)) {
            throw new IllegalArgumentException("任务节点不存在于绑定的流程版本: " + task.getTaskDefinitionKey());
        }
        NodeOperationPolicy policy = policyParser.parse(userTask);
        Map<String, Object> variables = task.getProcessInstanceId() == null
                ? Map.of()
                : safeVariables(task.getProcessInstanceId());
        return new TaskSnapshot(task, policy, variables, processStart(task.getProcessInstanceId()));
    }

    private FlowElement findElement(BpmnModel model, String elementId) {
        if (model == null || !StringUtils.hasText(elementId)) {
            return null;
        }
        for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
            FlowElement element = process.getFlowElement(elementId, true);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private Map<String, Object> safeVariables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            return variables == null ? Map.of() : Map.copyOf(variables);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private Instant processStart(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return null;
        }
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        Date startTime = instance == null ? null : instance.getStartTime();
        return startTime == null ? null : startTime.toInstant();
    }

    private Map<String, Object> conditionVariables(TaskSnapshot snapshot, CheckContext context) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(snapshot.processVariables());
        result.put("process", snapshot.processVariables());
        Object business = snapshot.processVariables().get("business");
        result.put("business", business instanceof Map<?, ?> ? business : Map.of());
        Task task = snapshot.task();
        result.put("task", task == null ? Map.of() : Map.of(
                "id", safe(task.getId()),
                "nodeId", safe(task.getTaskDefinitionKey()),
                "name", safe(task.getName()),
                "assignee", safe(task.getAssignee())));
        String currentUserId = StringUtils.hasText(context.currentUserId())
                ? context.currentUserId()
                : UserContext.getUserId();
        result.put("currentUser", Map.of("id", safe(currentUserId)));
        result.put("request", context.requestVariables());
        return Map.copyOf(result);
    }

    /**
     * 创建请求变量的只读快照，同时保留可选表单字段的 null 值。
     *
     * <p>{@link Map#copyOf(Map)} 会拒绝 null 值，但空的一对一子表等合法业务数据会以 null 表示；
     * 这里仍拒绝空变量名，并保持与原实现一致的防御性浅拷贝语义。</p>
     */
    private static Map<String, Object> immutableRequestVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>(variables.size());
        variables.forEach((key, value) -> snapshot.put(
                Objects.requireNonNull(key, "请求变量名不能为空"), value));
        return Collections.unmodifiableMap(snapshot);
    }

    private boolean hasPermission(String permissionCode, Set<String> override) {
        if (override == null) {
            return PermissionUtil.hasPermission(permissionCode);
        }
        return override.contains("*") || override.contains(permissionCode);
    }

    private boolean needsUserTarget(NodeOperationPolicy.Operation operation) {
        return operation == NodeOperationPolicy.Operation.TRANSFER
                || operation.isAddSign()
                || operation == NodeOperationPolicy.Operation.MANUAL_CC;
    }

    private String addSignTypeOf(NodeOperationPolicy.Operation operation) {
        return switch (operation) {
            case ADD_SIGN_BEFORE -> "BEFORE";
            case ADD_SIGN_AFTER -> "AFTER";
            case ADD_SIGN_PARALLEL -> "PARALLEL";
            default -> "";
        };
    }

    private ActionDecision denied(
            NodeOperationPolicy.Operation operation,
            String reasonCode,
            String message,
            NodeOperationPolicy.Rule rule) {
        return new ActionDecision(operation.apiCode(), false, reasonCode, message, rule);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record TaskSnapshot(
            Task task,
            NodeOperationPolicy policy,
            Map<String, Object> processVariables,
            Instant processStartedAt) {
    }

    /** 操作判定结果，同时携带前端渲染理由模板和目标约束所需的只读规则。 */
    public record ActionDecision(
            String operation,
            boolean allowed,
            String reasonCode,
            String message,
            NodeOperationPolicy.Rule rule) {
    }

    /**
     * 实际操作的输入上下文。permissions 为 null 时使用当前登录用户权限；模拟时传显式集合。
     */
    public record CheckContext(
            String reason,
            Set<String> targetUserIds,
            String targetNodeId,
            String addSignType,
            Map<String, Object> requestVariables,
            Set<String> permissions,
            String currentUserId,
            Instant now,
            boolean enforceRequestDetails) {

        public CheckContext {
            targetUserIds = targetUserIds == null ? Set.of() : Set.copyOf(targetUserIds);
            requestVariables = immutableRequestVariables(requestVariables);
            permissions = permissions == null ? null : Set.copyOf(permissions);
        }

        public static CheckContext availability() {
            return new CheckContext(null, Set.of(), null, null, Map.of(),
                    null, null, Instant.now(), false);
        }

        public static CheckContext request() {
            return new CheckContext(null, Set.of(), null, null, Map.of(),
                    null, null, Instant.now(), true);
        }

        public static CheckContext ofReason(String reason) {
            return new CheckContext(reason, Set.of(), null, null, Map.of(),
                    null, null, Instant.now(), true);
        }

        public static CheckContext ofTarget(
                String reason,
                Set<String> targetUserIds,
                String targetNodeId,
                String addSignType,
                Map<String, Object> requestVariables) {
            return new CheckContext(reason, targetUserIds, targetNodeId, addSignType,
                    requestVariables, null, null, Instant.now(), true);
        }

        private CheckContext withRequestDetails(boolean value) {
            return new CheckContext(reason, targetUserIds, targetNodeId, addSignType,
                    requestVariables, permissions, currentUserId, now, value);
        }
    }

    /** 设计器模拟请求上下文。 */
    public record SimulationContext(
            Map<String, Object> variables,
            Set<String> permissions,
            String currentUserId,
            String reason,
            Set<String> targetUserIds,
            String targetNodeId,
            String addSignType,
            Map<String, Object> requestVariables,
            Instant processStartedAt,
            Instant now) {

        public SimulationContext {
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
            targetUserIds = targetUserIds == null ? Set.of() : Set.copyOf(targetUserIds);
            requestVariables = immutableRequestVariables(requestVariables);
            now = now == null ? Instant.now() : now;
        }

        public static SimulationContext empty() {
            return new SimulationContext(Map.of(), Set.of(), null, null, Set.of(),
                    null, null, Map.of(), Instant.now(), Instant.now());
        }
    }

    /** 自动生成的矩阵测试覆盖项。 */
    public record CoverageCase(
            String operation,
            String scenarioType,
            String expected,
            String instruction) {
    }
}
