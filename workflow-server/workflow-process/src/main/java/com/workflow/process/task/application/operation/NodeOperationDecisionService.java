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

    /**
     * 返回当前用户在任务上的全部标准操作决策。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 可用动作集合键值结果，供调用方继续处理
     */
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
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param operation 操作标识，决定后续允许采用的处理分支
     * @param context 执行上下文，向后续允许步骤传递身份、配置或状态
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
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param operation 操作标识，决定后续允许流程采用的处理分支
     * @param context 执行上下文，向后续允许流程步骤传递身份、配置或状态
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
     *
     * @param policyJson 策略JSON，作为 {@code policyParser.parse} 的输入影响后续处理
     * @param operation 操作标识，决定后续{@code simulate}采用的处理分支
     * @param context 执行上下文，向后续{@code simulate}步骤传递身份、配置或状态
     * @return 处理后的{@code simulate}结果，供调用方继续处理
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
     *
     * @param policyJson 策略JSON，作为 {@code policyParser.parse} 的输入影响后续处理
     * @return {@code coverage}分支集合，供调用方遍历或展示
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

    /**
     * 处理{@code decide}，并将结果传给后续步骤。
     *
     * @param snapshot 快照，作为 {@code conditionVariables} 的输入影响后续处理
     * @param operation 操作标识，决定后续{@code decide}采用的处理分支
     * @param context 执行上下文，向后续{@code decide}步骤传递身份、配置或状态
     * @return 处理后的{@code decide}结果，供调用方继续处理
     */
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

    /**
     * 校验请求详情；不满足约束时阻止后续处理。
     *
     * @param operation 操作标识，决定后续请求详情采用的处理分支
     * @param rule 规则，作为 {@code denied} 的输入影响后续处理
     * @param context 执行上下文，向后续请求详情步骤传递身份、配置或状态
     * @return 校验后的请求详情结果，供调用方继续处理
     */
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

    /**
     * 处理快照，并将结果传给后续步骤。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 处理后的快照结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private TaskSnapshot snapshot(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return snapshot(task);
    }

    /**
     * 处理快照，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code repositoryService.getBpmnModel} 的输入影响后续处理
     * @return 处理后的快照结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 查询元素；查询结果供调用方展示或继续处理。
     *
     * @param model 模型，供本方法查询元素时使用
     * @param elementId 元素ID，后续用于查询元素时定位或关联目标
     * @return 符合条件的流程元素结果，供调用方继续处理
     */
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

    /**
     * 整理安全流程变量数据，供调用方遍历或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 安全流程变量键值结果，供调用方继续处理
     */
    private Map<String, Object> safeVariables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            return variables == null ? Map.of() : Map.copyOf(variables);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    /**
     * 处理启动，并将结果传给后续步骤。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 处理后的启动结果，供调用方继续处理
     */
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

    /**
     * 整理条件流程变量数据，供调用方遍历或继续处理。
     *
     * @param snapshot 快照，作为 {@code result.put} 的输入影响后续处理
     * @param context 执行上下文，向后续条件流程变量步骤传递身份、配置或状态
     * @return 条件流程变量键值结果，供调用方继续处理
     */
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
     *
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @return 不可变请求流程变量键值结果，供调用方继续处理
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

    /**
     * 判断是否具有权限；判断结果决定调用方的后续分支。
     *
     * @param permissionCode 权限编码，后续用于判断是否具有权限时定位或关联目标
     * @param override 覆盖，供本方法判断是否具有权限时使用
     * @return 权限条件成立时为 true，否则为 false
     */
    private boolean hasPermission(String permissionCode, Set<String> override) {
        if (override == null) {
            return PermissionUtil.hasPermission(permissionCode);
        }
        return override.contains("*") || override.contains(permissionCode);
    }

    /**
     * 判断{@code needs}用户目标条件是否成立，供调用方选择后续分支。
     *
     * @param operation 操作标识，决定后续{@code needs}用户目标采用的处理分支
     * @return {@code needs}用户目标条件成立时为 true，否则为 false
     */
    private boolean needsUserTarget(NodeOperationPolicy.Operation operation) {
        return operation == NodeOperationPolicy.Operation.TRANSFER
                || operation.isAddSign()
                || operation == NodeOperationPolicy.Operation.MANUAL_CC;
    }

    /**
     * 添加签名类型；结果供后续流程传递或持久化。
     *
     * @param operation 操作标识，决定后续签名类型采用的处理分支
     * @return 添加后的签名类型文本，供调用方比较或展示
     */
    private String addSignTypeOf(NodeOperationPolicy.Operation operation) {
        return switch (operation) {
            case ADD_SIGN_BEFORE -> "BEFORE";
            case ADD_SIGN_AFTER -> "AFTER";
            case ADD_SIGN_PARALLEL -> "PARALLEL";
            default -> "";
        };
    }

    /**
     * 处理已拒绝，并将结果传给后续步骤。
     *
     * @param operation 操作标识，决定后续已拒绝采用的处理分支
     * @param reasonCode 原因编码，后续用于处理已拒绝时定位或关联目标
     * @param message 消息，供本方法处理已拒绝时使用
     * @param rule 规则，供本方法处理已拒绝时使用
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private ActionDecision denied(
            NodeOperationPolicy.Operation operation,
            String reasonCode,
            String message,
            NodeOperationPolicy.Rule rule) {
        return new ActionDecision(operation.apiCode(), false, reasonCode, message, rule);
    }

    /**
     * 生成安全文本，供后续匹配或展示。
     *
     * @param value 待处理安全的原始输入，结果供调用方继续使用
     * @return 处理后的安全文本，供调用方比较或展示
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 封装任务快照的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param task 任务，保存在对象中供后续校验、查询或展示
     * @param policy 策略内容，决定后续任务快照的处理规则
     * @param processVariables 流程流程变量，保存在对象中供后续校验、查询或展示
     * @param processStartedAt 流程已启动时间，后续用于判断有效期或展示该事件的发生时间
     */
    private record TaskSnapshot(
            Task task,
            NodeOperationPolicy policy,
            Map<String, Object> processVariables,
            Instant processStartedAt) {
    }

    /**
     * 操作判定结果，同时携带前端渲染理由模板和目标约束所需的只读规则。
     *
     * @param operation 操作标识，决定后续动作决策采用的处理分支
     * @param allowed 允许，保存在对象中供后续校验、查询或展示
     * @param reasonCode 原因编码，后续用于处理动作决策时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param rule 规则，保存在对象中供后续校验、查询或展示
     */
    public record ActionDecision(
            String operation,
            boolean allowed,
            String reasonCode,
            String message,
            NodeOperationPolicy.Rule rule) {
    }

    /**
     * 实际操作的输入上下文。permissions 为 null 时使用当前登录用户权限；模拟时传显式集合。
     *
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param targetUserIds 目标用户ID 集合，保存在对象中供后续校验、查询或展示
     * @param targetNodeId 目标节点ID，后续用于处理检查上下文时定位或关联目标
     * @param addSignType 添加签名类型标识，决定后续检查上下文采用的处理分支
     * @param requestVariables 请求流程变量，保存在对象中供后续校验、查询或展示
     * @param permissions {@code permissions}，保存在对象中供后续校验、查询或展示
     * @param currentUserId 当前用户ID，后续用于处理检查上下文时定位或关联目标
     * @param now 当前时间，保存在对象中供后续校验、查询或展示
     * @param enforceRequestDetails {@code enforce}请求详情，保存在对象中供后续校验、查询或展示
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

        /**
         * 初始化检查上下文，保存构造参数供后续方法使用。
         *
         * @param reason 原因，保存在对象中供后续校验、查询或展示
         * @param targetUserIds 目标用户ID 集合，保存在对象中供后续校验、查询或展示
         * @param targetNodeId 目标节点ID，后续用于初始化检查上下文时定位或关联目标
         * @param addSignType 添加签名类型标识，决定后续检查上下文采用的处理分支
         * @param requestVariables 请求流程变量，保存在对象中供后续校验、查询或展示
         * @param permissions {@code permissions}，保存在对象中供后续校验、查询或展示
         * @param currentUserId 当前用户ID，后续用于初始化检查上下文时定位或关联目标
         * @param now 当前时间，保存在对象中供后续校验、查询或展示
         * @param enforceRequestDetails {@code enforce}请求详情，保存在对象中供后续校验、查询或展示
         */
        public CheckContext {
            targetUserIds = targetUserIds == null ? Set.of() : Set.copyOf(targetUserIds);
            requestVariables = immutableRequestVariables(requestVariables);
            permissions = permissions == null ? null : Set.copyOf(permissions);
        }

        /**
         * 处理{@code availability}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code availability}结果，供调用方继续处理
         */
        public static CheckContext availability() {
            return new CheckContext(null, Set.of(), null, null, Map.of(),
                    null, null, Instant.now(), false);
        }

        /**
         * 处理请求，并将结果传给后续步骤。
         *
         * @return 处理后的请求结果，供调用方继续处理
         */
        public static CheckContext request() {
            return new CheckContext(null, Set.of(), null, null, Map.of(),
                    null, null, Instant.now(), true);
        }

        /**
         * 处理原因，并将结果传给后续步骤。
         *
         * @param reason 原因，作为 {@code CheckContext} 的输入影响后续处理
         * @return 处理后的原因结果，供调用方继续处理
         */
        public static CheckContext ofReason(String reason) {
            return new CheckContext(reason, Set.of(), null, null, Map.of(),
                    null, null, Instant.now(), true);
        }

        /**
         * 处理目标，并将结果传给后续步骤。
         *
         * @param reason 原因，作为 {@code CheckContext} 的输入影响后续处理
         * @param targetUserIds 目标用户ID 集合，作为 {@code CheckContext} 的输入影响后续处理
         * @param targetNodeId 目标节点ID，后续用于处理目标时定位或关联目标
         * @param addSignType 添加签名类型标识，决定后续目标采用的处理分支
         * @param requestVariables 请求流程变量，作为 {@code CheckContext} 的输入影响后续处理
         * @return 处理后的目标结果，供调用方继续处理
         */
        public static CheckContext ofTarget(
                String reason,
                Set<String> targetUserIds,
                String targetNodeId,
                String addSignType,
                Map<String, Object> requestVariables) {
            return new CheckContext(reason, targetUserIds, targetNodeId, addSignType,
                    requestVariables, null, null, Instant.now(), true);
        }

        /**
         * 处理请求详情，并将结果传给后续步骤。
         *
         * @param value 待处理请求详情的原始输入，结果供调用方继续使用
         * @return 处理后的请求详情结果，供调用方继续处理
         */
        private CheckContext withRequestDetails(boolean value) {
            return new CheckContext(reason, targetUserIds, targetNodeId, addSignType,
                    requestVariables, permissions, currentUserId, now, value);
        }
    }

    /**
     * 设计器模拟请求上下文。
     *
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param permissions {@code permissions}，保存在对象中供后续校验、查询或展示
     * @param currentUserId 当前用户ID，后续用于处理{@code simulation}上下文时定位或关联目标
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param targetUserIds 目标用户ID 集合，保存在对象中供后续校验、查询或展示
     * @param targetNodeId 目标节点ID，后续用于处理{@code simulation}上下文时定位或关联目标
     * @param addSignType 添加签名类型标识，决定后续{@code simulation}上下文采用的处理分支
     * @param requestVariables 请求流程变量，保存在对象中供后续校验、查询或展示
     * @param processStartedAt 流程已启动时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，保存在对象中供后续校验、查询或展示
     */
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

        /**
         * 初始化{@code simulation}上下文，保存构造参数供后续方法使用。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @param permissions {@code permissions}，保存在对象中供后续校验、查询或展示
         * @param currentUserId 当前用户ID，后续用于初始化{@code simulation}上下文时定位或关联目标
         * @param reason 原因，保存在对象中供后续校验、查询或展示
         * @param targetUserIds 目标用户ID 集合，保存在对象中供后续校验、查询或展示
         * @param targetNodeId 目标节点ID，后续用于初始化{@code simulation}上下文时定位或关联目标
         * @param addSignType 添加签名类型标识，决定后续{@code simulation}上下文采用的处理分支
         * @param requestVariables 请求流程变量，保存在对象中供后续校验、查询或展示
         * @param processStartedAt 流程已启动时间，后续用于判断有效期或展示该事件的发生时间
         * @param now 当前时间，保存在对象中供后续校验、查询或展示
         */
        public SimulationContext {
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
            targetUserIds = targetUserIds == null ? Set.of() : Set.copyOf(targetUserIds);
            requestVariables = immutableRequestVariables(requestVariables);
            now = now == null ? Instant.now() : now;
        }

        /**
         * 处理空，并将结果传给后续步骤。
         *
         * @return 处理后的空结果，供调用方继续处理
         */
        public static SimulationContext empty() {
            return new SimulationContext(Map.of(), Set.of(), null, null, Set.of(),
                    null, null, Map.of(), Instant.now(), Instant.now());
        }
    }

    /**
     * 自动生成的矩阵测试覆盖项。
     *
     * @param operation 操作标识，决定后续{@code coverage}分支采用的处理分支
     * @param scenarioType {@code scenario}类型标识，决定后续{@code coverage}分支采用的处理分支
     * @param expected 预期，保存在对象中供后续校验、查询或展示
     * @param instruction {@code instruction}，保存在对象中供后续校验、查询或展示
     */
    public record CoverageCase(
            String operation,
            String scenarioType,
            String expected,
            String instruction) {
    }
}
