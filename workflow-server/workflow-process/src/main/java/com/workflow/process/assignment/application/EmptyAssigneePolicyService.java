package com.workflow.process.assignment.application;

import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
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

    /** 移除已被调用方确认无有效办理人的任务分配，再应用兜底或进入事件等待。 */
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

    public record EmptyContext(
            String processConfigId,
            String processKey,
            String resolverCode,
            Map<String, Object> extraParams) {
    }

    public record Outcome(
            String policy,
            String incidentId,
            boolean blocking,
            boolean fallbackApplied,
            List<String> fallbackUsers) {

        private static Outcome fallback(String policy, List<String> users) {
            return new Outcome(policy, null, false, true, List.copyOf(users));
        }
    }
}
