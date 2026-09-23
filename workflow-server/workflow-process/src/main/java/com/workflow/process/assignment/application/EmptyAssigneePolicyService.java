package com.workflow.process.assignment.application;

import com.workflow.contracts.process.assignment.model.PersonPrincipal;
import com.workflow.contracts.process.assignment.model.PersonPrincipalType;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import com.workflow.process.assignment.domain.EmptyAssigneePolicy;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.task.api.Task;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** 在任务创建边界执行五种空办理人策略。 */
@Service
@RequiredArgsConstructor
public class EmptyAssigneePolicyService {

    private final EmptyAssigneePolicyResolver policyResolver;
    private final AssigneeResolutionService resolutionService;
    private final AssigneeIncidentRecorder incidentRecorder;
    private final TaskService taskService;

    /**
     * 处理结构化空结果。兜底成功直接分配；其余策略创建可发现事件，BLOCK_PUBLISH 由调用方随后抛错回滚。
     *
     * @param task 任务，作为 {@code clearUnavailableAssignment} 的输入影响后续处理
     * @param bpmnModel BPMN模型，作为 {@code policyResolver.resolve} 的输入影响后续处理
     * @param assigneeConfig 办理人配置内容，决定后续空的处理规则
     * @param context 执行上下文，向后续空步骤传递身份、配置或状态
     * @param resolution 解析，供本方法处理空时使用
     * @return 处理后的空结果，供调用方继续处理
     */
    public Outcome handleEmpty(
            Task task,
            BpmnModel bpmnModel,
            Map<String, Object> assigneeConfig,
            EmptyContext context,
            AssigneeResolutionResult resolution) {
        EmptyAssigneePolicy policy = policyResolver.resolve(bpmnModel, assigneeConfig);
        // 无效旧执行人或候选组不能继续持有任务，否则兜底组成员仍可能无法认领，
        // 事件等待期间也可能被原候选身份误操作。保留 owner 等非候选身份用于审计。
        clearUnavailableAssignment(task);
        if (policy.strategy() == EmptyAssigneePolicy.Strategy.FALLBACK_USER) {
            AssigneeResolutionResult fallback = resolutionService.resolvePrincipals(
                    List.of(PersonPrincipal.user(policy.fallbackUser())),
                    "FALLBACK_USER_INVALID");
            if (fallback.resolved()) {
                taskService.setAssignee(task.getId(), fallback.usernames().get(0));
                return Outcome.fallback(policy.strategy().name(), fallback.usernames());
            }
            resolution = fallback;
        } else if (policy.strategy() == EmptyAssigneePolicy.Strategy.FALLBACK_GROUP) {
            AssigneeResolutionResult fallback = resolutionService.resolvePrincipals(
                    List.of(new PersonPrincipal(
                            PersonPrincipalType.GROUP, policy.fallbackGroup())),
                    "FALLBACK_GROUP_INVALID");
            if (fallback.resolved()) {
                taskService.addCandidateGroup(task.getId(), policy.fallbackGroup());
                return Outcome.fallback(policy.strategy().name(), fallback.usernames());
            }
            resolution = fallback;
        }

        boolean waiting = policy.strategy() == EmptyAssigneePolicy.Strategy.WAIT_AND_RETRY;
        String status = waiting ? "RETRY_SCHEDULED" : "OPEN";
        LocalDateTime nextRetry = waiting
                ? LocalDateTime.now().plusSeconds(policy.initialDelaySeconds())
                : null;
        String incidentId = incidentRecorder.create(new AssigneeIncidentRecorder.CreateCommand(
                context.processConfigId(), task.getProcessDefinitionId(),
                task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), policy.strategy().name(), status,
                resolution.reasonCode() == null ? "ASSIGNEE_EMPTY" : resolution.reasonCode(),
                resolution.reasonMessage(), context.resolverCode(), context.extraParams(),
                policy.fallbackUser(), policy.fallbackGroup(), policy.responsibilityOwner(),
                waiting ? policy.maxRetries() : 0, policy.initialDelaySeconds(),
                policy.backoffMultiplier(), nextRetry,
                Map.of(
                        "resolutionStatus", resolution.status().name(),
                        "processKey", context.processKey() == null ? "" : context.processKey())));
        try {
            taskService.setVariableLocal(task.getId(), "wfAssigneeIncidentId", incidentId);
            taskService.setVariableLocal(task.getId(), "wfAssigneeIncidentStatus", status);
        } catch (RuntimeException ignored) {
            // BLOCK_PUBLISH 可能随即回滚任务；incident 已在独立事务中持久化。
        }
        return new Outcome(
                policy.strategy().name(), incidentId,
                policy.strategy() == EmptyAssigneePolicy.Strategy.BLOCK_PUBLISH,
                false, List.of());
    }

    /**
     * 移除已被调用方确认无有效办理人的任务分配，再应用兜底或进入事件等待。
     *
     * @param task 任务，作为 {@code taskService.setAssignee} 的输入影响后续处理
     */
    private void clearUnavailableAssignment(Task task) {
        taskService.setAssignee(task.getId(), null);
        var links = taskService.getIdentityLinksForTask(task.getId());
        if (links == null) return;
        for (var link : new ArrayList<>(links)) {
            if (!"candidate".equals(link.getType())) continue;
            if (link.getUserId() != null) taskService.deleteCandidateUser(task.getId(), link.getUserId());
            if (link.getGroupId() != null) taskService.deleteCandidateGroup(task.getId(), link.getGroupId());
        }
    }

    /**
     * 封装空上下文的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param processConfigId 流程配置 ID，后续定位已发布的节点配置
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param resolverCode 解析器编码，后续用于处理空上下文时定位或关联目标
     * @param extraParams 附加参数，后续传给解析器或执行器
     */
    public record EmptyContext(
            String processConfigId,
            String processKey,
            String resolverCode,
            Map<String, Object> extraParams) {
    }

    /**
     * 封装结果的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param policy 策略内容，决定后续结果的处理规则
     * @param incidentId 异常事件ID，后续用于处理结果时定位或关联目标
     * @param blocking {@code blocking}，保存在对象中供后续校验、查询或展示
     * @param fallbackApplied 兜底{@code applied}，主值不可用时供后续处理兜底
     * @param fallbackUsers 兜底用户集合，主值不可用时供后续处理兜底
     */
    public record Outcome(
            String policy,
            String incidentId,
            boolean blocking,
            boolean fallbackApplied,
            List<String> fallbackUsers) {

        /**
         * 处理兜底，并将结果传给后续步骤。
         *
         * @param policy 策略内容，决定后续兜底的处理规则
         * @param users 用户集合，作为 {@code Outcome} 的输入影响后续处理
         * @return 处理后的兜底结果，供调用方继续处理
         */
        private static Outcome fallback(String policy, List<String> users) {
            return new Outcome(policy, null, false, true, List.copyOf(users));
        }
    }
}
