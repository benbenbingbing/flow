package com.workflow.process.task.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import com.workflow.process.task.infrastructure.MultiInstanceVariableNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 多实例办理结果（通过人数、是否汇聚结束）的唯一写入与判断入口。
 *
 * <p>会签按票数模型：通过只加通过人数；驳回不加。未开「全部办完」时，
 * 达标立即通过，剩下的人全通过也凑不够则立即拒绝。开了「全部办完」则等全员
 * 再按通过率判定。或签仍是一人通过或一人驳回即结束。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiInstanceOutcomeService {

    public static final String DECISION_COUNTERSIGN = "countersign";
    public static final String DECISION_ORSIGN = "orsign";
    public static final int DEFAULT_COMPLETION_RATE = 100;
    public static final int MIN_COMPLETION_RATE = 1;

    private static final int INCREMENT_RETRY_TIMES = 2;

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    /**
     * 判断任务是否绑定了已部署 BPMN 上的多实例用户任务。
     * 不用 nrOfInstances 启发式，避免套在多实例子流程里的普通任务被误计数。
     *
     * @param task 任务，作为 {@code deployedUserTask} 的输入影响后续处理
     * @return 多实例条件成立时为 true，否则为 false
     */
    public boolean isMultiInstance(Task task) {
        return deployedUserTask(task)
                .map(UserTask::hasMultiInstanceLoopCharacteristics)
                .orElse(false);
    }

    /**
     * 规范化审批动作。自定义动作保持原值，不计入通过人数、不触发否决。
     *
     * @param action 动作标识，决定后续动作采用的处理分支
     * @return 规范化后的动作文本，供调用方比较或展示
     */
    public String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "approve";
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> "approve";
            case "REJECT", "REJECTED" -> "reject";
            case "TRANSFER", "TRANSFERRED" -> "transfer";
            default -> action.trim();
        };
    }

    /**
     * 通过时给当前节点通过人数 +1。非多实例或非 approve 不处理。
     *
     * @param task 任务，作为 {@code incrementApprovedCount} 的输入影响后续处理
     */
    public void recordApprove(Task task) {
        if (task == null || !isMultiInstance(task)) {
            return;
        }
        incrementApprovedCount(task.getProcessInstanceId(),
                MultiInstanceVariableNames.buildApprovedCountVariableName(
                        task.getTaskDefinitionKey()));
    }

    /**
     * 或签驳回才打一票否决标记。会签驳回只表示这张票不是通过，不加通过人数。
     *
     * @param task 任务，作为 {@code deployedUserTask} 的输入影响后续处理
     */
    public void recordReject(Task task) {
        if (task == null || !isMultiInstance(task)) {
            return;
        }
        UserTask userTask = deployedUserTask(task).orElse(null);
        if (userTask == null) {
            return;
        }
        if (!DECISION_ORSIGN.equals(readSettings(userTask).decision())) {
            return;
        }
        runtimeService.setVariable(
                task.getProcessInstanceId(),
                MultiInstanceVariableNames.buildRejectedVariableName(
                        task.getTaskDefinitionKey()),
                true);
    }

    /**
     * 或签是否已被一票否决。会签不再使用该标记提前结束。
     *
     * @param task 任务，作为 {@code isTruthy} 的输入影响后续处理
     * @return 已拒绝条件成立时为 true，否则为 false
     */
    public boolean isRejected(Task task) {
        if (task == null || !StringUtils.hasText(task.getTaskDefinitionKey())) {
            return false;
        }
        return isTruthy(runtimeService.getVariable(
                task.getProcessInstanceId(),
                MultiInstanceVariableNames.buildRejectedVariableName(
                        task.getTaskDefinitionKey())));
    }

    /**
     * 写入流程变量 {@code approved} 的值。
     * 会签未决出结果时，中间驳回不得把网关结果写成 reject。
     *
     * @param task 任务，作为 {@code project} 的输入影响后续处理
     * @param normalizedAction 规范化动作，作为 {@code normalizeAction} 的输入影响后续处理
     * @return 解析后的{@code approved}结果文本，供调用方比较或展示
     */
    public String resolveApprovedOutcome(Task task, String normalizedAction) {
        String action = normalizeAction(normalizedAction);
        if (!isMultiInstance(task)) {
            if ("reject".equals(action)) {
                return "reject";
            }
            return "approve".equals(action) ? "approve" : action;
        }
        MultiInstanceProjection projection = project(task, action);
        if (projection == MultiInstanceProjection.PASS) {
            return "approve";
        }
        if (projection == MultiInstanceProjection.FAIL) {
            return "reject";
        }
        if ("approve".equals(action)) {
            return "approve";
        }
        Object existing = runtimeService.getVariable(
                task.getProcessInstanceId(), "approved");
        if (existing == null) {
            return null;
        }
        String current = String.valueOf(existing);
        return "approve".equals(current) ? "approve" : current;
    }

    /**
     * 本次办理是否会使当前多实例节点汇聚结束，从而允许指定下一审批人。
     *
     * @param task 任务，作为 {@code project} 的输入影响后续处理
     * @param action 动作标识，决定后续{@code will}{@code finish}当前节点采用的处理分支
     * @return {@code will}{@code finish}当前节点条件成立时为 true，否则为 false
     */
    public boolean willFinishCurrentNode(Task task, String action) {
        return project(task, action) != MultiInstanceProjection.CONTINUE;
    }

    /**
     * 按票数模型预估本次办理后的节点结果。
     *
     * @param task 任务，作为 {@code deployedUserTask} 的输入影响后续处理
     * @param action 动作标识，决定后续项目采用的处理分支
     * @return 处理后的项目结果，供调用方继续处理
     */
    public MultiInstanceProjection project(Task task, String action) {
        UserTask userTask = deployedUserTask(task).orElse(null);
        if (userTask == null || !userTask.hasMultiInstanceLoopCharacteristics()) {
            return MultiInstanceProjection.PASS;
        }
        String normalizedAction = normalizeAction(action);
        if ("transfer".equals(normalizedAction)) {
            return MultiInstanceProjection.CONTINUE;
        }
        MultiInstanceNodeSettings settings = readSettings(userTask);
        if (DECISION_ORSIGN.equals(settings.decision())) {
            if ("approve".equals(normalizedAction) || "reject".equals(normalizedAction)) {
                return "reject".equals(normalizedAction)
                        ? MultiInstanceProjection.FAIL
                        : MultiInstanceProjection.PASS;
            }
            return MultiInstanceProjection.CONTINUE;
        }
        int instanceCount = asInt(localOrProcessVariable(task, "nrOfInstances"), 0);
        int completedCount = asInt(localOrProcessVariable(task, "nrOfCompletedInstances"), 0);
        int approvedCount = asInt(runtimeService.getVariable(
                task.getProcessInstanceId(),
                MultiInstanceVariableNames.buildApprovedCountVariableName(
                        task.getTaskDefinitionKey())), 0);
        if (instanceCount <= 0) {
            return MultiInstanceProjection.CONTINUE;
        }
        int nextApproved = approvedCount
                + ("approve".equals(normalizedAction) ? 1 : 0);
        int nextCompleted = completedCount + 1;
        boolean rateMet = nextApproved * 100 >= instanceCount * settings.completionRate();
        boolean remainingCannotMeet =
                (nextApproved + instanceCount - nextCompleted) * 100
                        < instanceCount * settings.completionRate();
        if (settings.needAllApprovers()) {
            if (nextCompleted < instanceCount) {
                return MultiInstanceProjection.CONTINUE;
            }
            return rateMet
                    ? MultiInstanceProjection.PASS
                    : MultiInstanceProjection.FAIL;
        }
        if (rateMet) {
            return MultiInstanceProjection.PASS;
        }
        if (remainingCannotMeet) {
            return MultiInstanceProjection.FAIL;
        }
        return MultiInstanceProjection.CONTINUE;
    }

    /**
     * 定义多实例投影的可选值；调用方据此选择对应的处理分支。
     */
    public enum MultiInstanceProjection {
        PASS,
        FAIL,
        CONTINUE
    }

    /**
     * 从已部署节点的 assigneeConfig / multiInstanceConfig 读取办理模式与阈值。
     *
     * @param userTask 用户任务，作为 {@code parseObject} 的输入影响后续处理
     * @return 读取后的{@code settings}结果，供调用方继续处理
     */
    public MultiInstanceNodeSettings readSettings(UserTask userTask) {
        Map<String, Object> assigneeConfig = parseObject(
                ConfiguredTaskPropertyReader.read(userTask, "assigneeConfig"));
        Map<String, Object> multiInstanceConfig = parseObject(
                ConfiguredTaskPropertyReader.read(userTask, "multiInstanceConfig"));
        String decision = firstText(
                assigneeConfig.get("multiInstanceDecision"),
                multiInstanceConfig.get("multiInstanceDecision"));
        Object rate = firstValue(
                multiInstanceConfig.get("multiInstanceCompletionRate"),
                assigneeConfig.get("multiInstanceCompletionRate"));
        Object needAll = firstValue(
                multiInstanceConfig.get("multiInstanceNeedAllApprovers"),
                assigneeConfig.get("multiInstanceNeedAllApprovers"));
        return new MultiInstanceNodeSettings(
                normalizeDecision(decision),
                normalizeCompletionRate(rate),
                isTruthy(needAll));
    }

    /**
     * 处理{@code increment}{@code approved}数量，并将结果传给后续步骤。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param approvedCountVariableName {@code approved}数量变量名称，后续用于处理{@code increment}{@code approved}数量时匹配或展示
     */
    private void incrementApprovedCount(
            String processInstanceId,
            String approvedCountVariableName) {
        // 并行会签两人同时 +1 时，第二次写入会撞上 Flowable 执行实体乐观锁。
        // 同一 complete 命令内重试一次即可；仍失败则让办理失败，避免少计通过人数。
        FlowableOptimisticLockingException lastConflict = null;
        for (int attempt = 1; attempt <= INCREMENT_RETRY_TIMES; attempt++) {
            try {
                int current = asInt(runtimeService.getVariable(
                        processInstanceId, approvedCountVariableName), 0);
                runtimeService.setVariable(
                        processInstanceId,
                        approvedCountVariableName,
                        current + 1);
                return;
            } catch (FlowableOptimisticLockingException exception) {
                lastConflict = exception;
                log.debug(
                        "多实例通过人数写入冲突，准备重试: processInstanceId={}, variable={}, attempt={}",
                        processInstanceId,
                        approvedCountVariableName,
                        attempt);
            }
        }
        throw lastConflict;
    }

    /**
     * 处理{@code deployed}用户任务，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code repositoryService.getBpmnModel} 的输入影响后续处理
     * @return 处理后的{@code deployed}用户任务结果，供调用方继续处理
     */
    private java.util.Optional<UserTask> deployedUserTask(Task task) {
        if (task == null
                || !StringUtils.hasText(task.getProcessDefinitionId())
                || !StringUtils.hasText(task.getTaskDefinitionKey())) {
            return java.util.Optional.empty();
        }
        BpmnModel model = repositoryService.getBpmnModel(
                task.getProcessDefinitionId());
        if (model == null || model.getMainProcess() == null) {
            return java.util.Optional.empty();
        }
        FlowElement element = model.getMainProcess().getFlowElement(
                task.getTaskDefinitionKey(), true);
        return element instanceof UserTask userTask
                ? java.util.Optional.of(userTask)
                : java.util.Optional.empty();
    }

    /**
     * 处理本地或流程变量，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code taskService.getVariableLocal} 的输入影响后续处理
     * @param name 名称，后续用于处理本地或流程变量时匹配或展示
     * @return 处理后的本地或流程变量结果，供调用方继续处理
     */
    private Object localOrProcessVariable(Task task, String name) {
        try {
            Object local = taskService.getVariableLocal(task.getId(), name);
            if (local != null) {
                return local;
            }
        } catch (RuntimeException ignored) {
            // 任务局部变量不存在时回退到执行/流程变量
        }
        Object fromTask = taskService.getVariable(task.getId(), name);
        if (fromTask != null) {
            return fromTask;
        }
        return runtimeService.getVariable(task.getProcessInstanceId(), name);
    }

    /**
     * 解析对象；输出作为后续校验或处理的输入。
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 对象键值结果，供调用方继续处理
     */
    private Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() {
                    });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception exception) {
            log.debug("解析多实例节点配置失败: {}", exception.getMessage());
            return Map.of();
        }
    }

    /**
     * 规范化决策；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化决策的原始输入，结果供调用方继续使用
     * @return 规范化后的决策文本，供调用方比较或展示
     */
    public static String normalizeDecision(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("orsign".equals(normalized)
                || "or_sign".equals(normalized)
                || "or".equals(normalized)
                || "any".equals(normalized)) {
            return DECISION_ORSIGN;
        }
        return DECISION_COUNTERSIGN;
    }

    /**
     * 规范化{@code completion}频率；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化{@code completion}频率的原始输入，结果供调用方继续使用
     * @return 规范化后的{@code completion}频率结果，供调用方继续处理
     */
    public static int normalizeCompletionRate(Object value) {
        int parsed = asInt(value, DEFAULT_COMPLETION_RATE);
        if (parsed < MIN_COMPLETION_RATE) {
            return MIN_COMPLETION_RATE;
        }
        return Math.min(100, parsed);
    }

    /**
     * 转换为整数；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为整数的原始输入，结果供调用方继续使用
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 转换为后的整数结果，供调用方继续处理
     */
    static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /**
     * 判断是否{@code truthy}；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否{@code truthy}的原始输入，结果供调用方继续使用
     * @return {@code truthy}条件成立时为 true，否则为 false
     */
    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text)
                || "1".equals(text)
                || "yes".equalsIgnoreCase(text);
    }

    /**
     * 处理首个值，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个值结果，供调用方继续处理
     */
    private static Object firstValue(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    /**
     * 已部署多实例节点的办理语义。
     *
     * @param decision 决策，保存在对象中供后续校验、查询或展示
     * @param completionRate {@code completion}频率，保存在对象中供后续校验、查询或展示
     * @param needAllApprovers {@code need}全部{@code approvers}，保存在对象中供后续校验、查询或展示
     */
    public record MultiInstanceNodeSettings(
            String decision,
            int completionRate,
            boolean needAllApprovers) {
    }
}
