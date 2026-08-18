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
     */
    public boolean isMultiInstance(Task task) {
        return deployedUserTask(task)
                .map(UserTask::hasMultiInstanceLoopCharacteristics)
                .orElse(false);
    }

    /**
     * 规范化审批动作。自定义动作保持原值，不计入通过人数、不触发否决。
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
     */
    public boolean willFinishCurrentNode(Task task, String action) {
        return project(task, action) != MultiInstanceProjection.CONTINUE;
    }

    /**
     * 按票数模型预估本次办理后的节点结果。
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

    public enum MultiInstanceProjection {
        PASS,
        FAIL,
        CONTINUE
    }

    /**
     * 从已部署节点的 assigneeConfig / multiInstanceConfig 读取办理模式与阈值。
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

    public static int normalizeCompletionRate(Object value) {
        int parsed = asInt(value, DEFAULT_COMPLETION_RATE);
        if (parsed < MIN_COMPLETION_RATE) {
            return MIN_COMPLETION_RATE;
        }
        return Math.min(100, parsed);
    }

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

    private static Object firstValue(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

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
     */
    public record MultiInstanceNodeSettings(
            String decision,
            int completionRate,
            boolean needAllApprovers) {
    }
}
