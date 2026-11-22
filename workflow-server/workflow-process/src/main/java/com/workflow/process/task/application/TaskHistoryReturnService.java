package com.workflow.process.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.contracts.process.task.model.TaskFormReference;
import com.workflow.process.action.application.FlowActionOperationDispatcher;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.command.application.ProcessCommandExecutor;
import com.workflow.process.command.application.ProcessCommandFailures;
import com.workflow.process.command.application.ProcessInvocationAccess;
import com.workflow.process.status.application.ProcessEndReason;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import com.workflow.process.task.application.operation.NodeHistoryReturnPolicy;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.workflow.contracts.process.action.model.FlowActionTriggerTiming.TASK_RETURNED;
import static com.workflow.contracts.process.action.model.FlowActionTriggerTiming.TASK_RETURNING;

/** 在已发布的一段单执行路径中重开历史用户任务，保留原实例和历史。 */
@Service
@RequiredArgsConstructor
public class TaskHistoryReturnService {
    public static final String OPERATION = "RETURN";
    public static final String RESUBMIT_MARKER = "_wfHistoryReturnResubmit";
    private static final Comparator<HistoricActivityInstance> HISTORY_ORDER = Comparator
            .comparing(HistoricActivityInstance::getStartTime)
            .thenComparing(HistoricActivityInstance::getTransactionOrder, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final TaskService tasks;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final RepositoryService repository;
    private final TaskActionService taskActions;
    private final TaskAddSignService addSigns;
    private final TaskSubmissionGuard forms;
    private final ProcessTaskService mirrors;
    private final NodeHistoryReturnPolicy policy;
    private final ProcessInvocationAccess access;
    private final ProcessCommandExecutor commands;
    private final FlowActionOperationDispatcher flowActions;
    private final ProcessOperationLogMapper logs;
    private final ObjectMapper json;
    private final IdentityDirectoryPort identities;

    public record Request(@NotBlank @Size(max = 200) String requestId,
                          @NotBlank @Size(max = 128) String targetActivityInstanceId,
                          @NotBlank @Size(max = 2000) String comment,
                          String formId, String formConfigHash) {}

    public record Target(String activityInstanceId, String nodeId, String nodeName, boolean resubmit) {}
    public record Outcome(String processInstanceId, String targetNodeId, String requestId) {}
    public record ReturnHistory(String targetActivityInstanceId, String targetNodeId,
                                String targetTaskId, List<String> invalidatedActivityIds) {}

    /** 用当前身份和冻结版本查询候选；完整校验也会在写事务中重新运行。 */
    @Transactional(readOnly = true)
    public List<Target> targets(String taskId) {
        Task task = tasks.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) return List.of();
        taskActions.requireTaskAccess(taskId);
        return candidates(task).values().stream().map(Candidate::target).toList();
    }

    /** 重提能力绑定新待办，查询人已由任务能力入口验证。 */
    public boolean canResubmit(String taskId) {
        return tasks.createTaskQuery().taskId(taskId).active().taskVariableValueEquals(RESUBMIT_MARKER, true).count() == 1;
    }

    /** 幂等回执、引擎移动、动作与待办投影由同一个写事务提交。 */
    public Outcome execute(String taskId, Request request) {
        var actor = access.requireActor();
        return ProcessCommandFailures.translate(taskId, () -> commands.execute(
                new CommandMeta(request.requestId()), "TASK_HISTORY_RETURN", Map.of("taskId", taskId, "input", request),
                Outcome.class, () -> TaskCommandScope.run(taskId,
                        () -> move(taskId, request, actor.username(), actor.userId())))).value();
    }

    private ProcessCommandExecutor.Outcome<Outcome> move(String taskId, Request request, String username, String userId) {
        Task source = tasks.createTaskQuery().taskId(taskId).active().singleResult();
        if (source == null) throw changed(taskId);
        taskActions.requireTaskAccess(taskId);
        Candidate selected = requireCandidate(source, request.targetActivityInstanceId());
        // 加签入口也锁源待办。等锁后用当前读检查加签记录，避免存储快照漏掉刚提交的编排。
        if (!mirrors.lockPendingHistoryReturnSource(taskId) || addSigns.hasOpenAddSignForUpdate(taskId)) {
            throw changed(taskId);
        }
        String instanceId = source.getProcessInstanceId();
        if (runtime.getVariable(instanceId, "entityCode") != null || request.formId() != null || request.formConfigHash() != null) {
            var reference = request.formId() == null && request.formConfigHash() == null ? null
                    : new TaskFormReference(request.formId(), request.formConfigHash());
            forms.require(taskId, source, reference);
        }
        String operator = StringUtils.hasText(username) ? username : userId;
        Target target = selected.target();
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("comment", request.comment().strip());
        parameters.put("targetNodeId", target.nodeId());
        parameters.put("targetActivityInstanceId", target.activityInstanceId());
        var event = flowActions.task(source, taskId, OPERATION, operator, null, parameters);
        event.setSourceNodeId(source.getTaskDefinitionKey());
        event.setSourceNodeName(source.getName());
        event.setTargetNodeId(target.nodeId());
        event.setTargetNodeName(target.nodeName());
        flowActions.fire(event, TASK_RETURNING);

        // 前置动作可以修改变量。重新核对目标和任务归属，候选任务在同一事务中完成认领。
        Task current = tasks.createTaskQuery().taskId(taskId).active().singleResult();
        if (current == null || !Objects.equals(source.getExecutionId(), current.getExecutionId())) throw changed(taskId);
        taskActions.requireTaskAccess(taskId);
        Candidate verified = requireCandidate(current, target.activityInstanceId());
        if (!selected.equals(verified)) throw changed(taskId);
        if (!StringUtils.hasText(current.getAssignee())) taskActions.claimTask(taskId);
        clearRoutingState(instanceId, selected);

        runtime.createChangeActivityStateBuilder().processInstanceId(instanceId)
                .moveExecutionToActivityId(source.getExecutionId(), target.nodeId()).changeState();

        List<Task> after = tasks.createTaskQuery().processInstanceId(instanceId).active().list();
        if (after.size() != 1 || taskId.equals(after.get(0).getId())
                || !target.nodeId().equals(after.get(0).getTaskDefinitionKey())) {
            throw new ProcessOperationException("TASK_STATE_CHANGED", "回退目标未生成预期待办", taskId);
        }
        Task next = after.get(0);
        if (target.resubmit()) {
            if (!assignedToStarter(next, starter(instanceId))) {
                throw new ProcessOperationException("TASK_ACTION_NOT_ALLOWED", "重提节点必须分配给发起人", taskId);
            }
            tasks.setVariableLocal(next.getId(), RESUBMIT_MARKER, true);
        } else if (!hasAssigneeOrCandidate(next)) {
            throw new ProcessOperationException("TASK_ACTION_NOT_ALLOWED", "回退目标未生成可办理的待办", taskId);
        }
        mirrors.completeTaskAfterHistoryReturn(taskId, request.comment().strip());
        mirrors.syncTasksAfterHistoryReturn(instanceId);
        recordReturn(source, next, selected, request.comment().strip(), operator);
        parameters.put("targetTaskId", next.getId());
        event.setOperationParameters(Map.copyOf(parameters));
        flowActions.fire(event, TASK_RETURNED);
        return new ProcessCommandExecutor.Outcome<>(instanceId, taskId, true,
                new Outcome(instanceId, target.nodeId(), request.requestId()));
    }

    private Candidate requireCandidate(Task source, String activityId) {
        Candidate result = candidates(source).get(activityId);
        if (result == null) throw changed(source.getId());
        return result;
    }

    private record Candidate(Target target, List<String> invalidatedActivityIds, Set<String> affectedNodeIds) {}

    /** 以当前历史活动为锚点反向读取单条执行链。碰到未经支持的活动时停止，不向更早活动猜测。 */
    private Map<String, Candidate> candidates(Task source) {
        var model = repository.getBpmnModel(source.getProcessDefinitionId());
        var process = model == null ? null : model.getMainProcess();
        var node = process == null ? null : process.getFlowElement(source.getTaskDefinitionKey());
        if (!(node instanceof UserTask) || !NodeHistoryReturnPolicy.supports(node)
                || !NodeHistoryReturnPolicy.supportsRootScope(process)) return Map.of();
        var config = policy.read(node);
        if (config.mode() == NodeHistoryReturnPolicy.Mode.OFF
                || addSigns.isAddSignSourceTask(source.getId()) || !hasSingleExecution(source)) return Map.of();

        String instanceId = source.getProcessInstanceId();
        var activities = history.createHistoricActivityInstanceQuery().processInstanceId(instanceId).list();
        var byExecution = new HashMap<String, List<HistoricActivityInstance>>();
        var byId = new HashMap<String, HistoricActivityInstance>();
        HistoricActivityInstance sourceActivity = null;
        for (var activity : activities) {
            byId.put(activity.getId(), activity);
            byExecution.computeIfAbsent(activity.getExecutionId(), id -> new ArrayList<>()).add(activity);
            if (source.getId().equals(activity.getTaskId())) sourceActivity = activity;
        }
        if (sourceActivity == null) return Map.of();
        byExecution.values().forEach(group -> group.sort(HISTORY_ORDER));
        Map<String, ReturnHistory> restored = previousReturns(instanceId);
        // Flowable 原生跳过也会写历史任务。业务镜像中的实际办理结果才能证明该任务可重开。
        Set<String> handledTasks = new HashSet<>();
        for (var mirror : mirrors.getTasksByProcessInstance(instanceId)) {
            if (ProcessTask.STATUS_DONE.equals(mirror.getStatus()) && StringUtils.hasText(mirror.getAction())
                    && !"return".equals(mirror.getAction()) && !"skip".equals(mirror.getAction())) {
                handledTasks.add(mirror.getTaskId());
            }
        }
        var invalidated = new ArrayList<String>();
        var affectedNodes = new HashSet<String>();
        var seenActivities = new HashSet<String>();
        var shownNodes = new HashSet<String>();
        Map<String, Candidate> result = new LinkedHashMap<>();
        affectedNodes.add(node.getId());
        shownNodes.add(node.getId());
        String startedBy = config.resubmitNodeId() == null ? null : starter(instanceId);

        for (var cursor = sourceActivity; cursor != null && seenActivities.add(cursor.getId());) {
            var bridge = restored.get(cursor.getTaskId());
            var anchor = bridge == null ? cursor : byId.get(bridge.targetActivityInstanceId());
            if (anchor == null || !cursor.getActivityId().equals(anchor.getActivityId())) break;
            var trail = byExecution.get(anchor.getExecutionId());
            int index = trail.indexOf(anchor);
            var nextNode = process.getFlowElement(anchor.getActivityId());
            if (index < 1 || !(nextNode instanceof FlowNode)) break;
            var preceding = trail.get(index - 1);
            var precedingElement = process.getFlowElement(preceding.getActivityId());
            // 某些历史级别包含连线记录。使用发生过的连线校验前后活动，避免误连循环或分支。
            if (precedingElement instanceof SequenceFlow flow) {
                if (index < 2 || !flow.getTargetRef().equals(nextNode.getId())) break;
                var beforeFlow = trail.get(index - 2);
                if (!flow.getSourceRef().equals(beforeFlow.getActivityId())
                        || HISTORY_ORDER.compare(preceding, anchor) >= 0) break;
                index--;
                preceding = beforeFlow;
                precedingElement = process.getFlowElement(preceding.getActivityId());
            }
            if (!(precedingElement instanceof FlowNode previous) || !NodeHistoryReturnPolicy.supports(previous)
                    || previous.getOutgoingFlows().stream().noneMatch(flow -> flow.getTargetRef().equals(nextNode.getId()))
                    || HISTORY_ORDER.compare(preceding, trail.get(index)) >= 0
                    || preceding.getEndTime() == null || ProcessEndReason.isCancelled(preceding.getDeleteReason())) break;
            cursor = preceding;
            affectedNodes.add(previous.getId());
            if (previous instanceof ExclusiveGateway || !handledTasks.contains(cursor.getTaskId())) continue;
            invalidated.add(cursor.getId());
            boolean resubmit = previous.getId().equals(config.resubmitNodeId());
            if (config.permits(previous.getId()) && (!resubmit || StringUtils.hasText(startedBy)) && shownNodes.add(previous.getId())) {
                var target = new Target(cursor.getId(), previous.getId(),
                        StringUtils.hasText(previous.getName()) ? previous.getName() : previous.getId(), resubmit);
                result.put(cursor.getId(), new Candidate(target, List.copyOf(invalidated), Set.copyOf(affectedNodes)));
            }
        }
        return result;
    }

    private boolean hasSingleExecution(Task source) {
        String instance = source.getProcessInstanceId();
        var executions = runtime.createExecutionQuery().processInstanceId(instance).list();
        var children = executions.stream().filter(execution -> execution.getParentId() != null).toList();
        // 汇聚点上的非活动 execution 也必须计入，防止把等待中的并行分支当作串行。
        return children.size() == 1 && isSourceExecution(children.get(0), source, instance);
    }

    private boolean isSourceExecution(Execution execution, Task source, String instance) {
        return instance.equals(execution.getParentId()) && source.getExecutionId().equals(execution.getId())
                && source.getTaskDefinitionKey().equals(execution.getActivityId()) && !execution.isSuspended();
    }

    private Map<String, ReturnHistory> previousReturns(String instanceId) {
        var records = logs.selectList(Wrappers.<ProcessOperationLog>lambdaQuery()
                .eq(ProcessOperationLog::getProcessInstanceId, instanceId).eq(ProcessOperationLog::getOperationType, OPERATION));
        Map<String, ReturnHistory> byTask = new HashMap<>();
        for (var record : records) {
            if (!OPERATION.equals(record.getOperationType()) || record.getNewValue() == null) continue;
            try {
                var change = json.readValue(record.getNewValue(), ReturnHistory.class);
                if (change.targetTaskId() != null) byTask.put(change.targetTaskId(), change);
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("流程回退历史无法读取", failure);
            }
        }
        return byTask;
    }

    private void clearRoutingState(String instanceId, Candidate selected) {
        // approved 等为上一办理的路由结果。只删除经回退失效的选人覆盖，保留其他节点条目。
        runtime.removeVariables(instanceId, List.of("approved", "action", "actionLabel", "approver", "comment",
                "rejectBy", "rejectTime", "_rejected_", "_rejectTarget_", "_rejectBy_", "_rejectTime_",
                "_rejectComment_", "_rejectSourceTaskId_", "_rejectSourceNodeId_",
                "_resubmitted_", "_resubmitTime_", "_resubmitComment_", "_resubmitBy_", "resubmitted"));
        String variable = NextApproverOverrideStore.VARIABLE_NAME;
        if (runtime.getVariable(instanceId, variable) instanceof Map<?, ?> previous) {
            var retained = new LinkedHashMap<>(previous);
            retained.keySet().removeIf(selected.affectedNodeIds()::contains);
            if (retained.isEmpty()) runtime.removeVariable(instanceId, variable);
            else runtime.setVariable(instanceId, variable, retained);
        }
    }

    private String starter(String instanceId) {
        var instance = history.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        return instance == null ? null : instance.getStartUserId();
    }

    /** 发起人标识可能落库为 ID，用户任务分配可能使用同一用户的登录名。 */
    private boolean assignedToStarter(Task task, String starter) {
        String assignee = task.getAssignee();
        return StringUtils.hasText(assignee) && StringUtils.hasText(starter)
                && (starter.equals(assignee) || identities.findUser(starter)
                .filter(user -> assignee.equals(user.id()) || assignee.equals(user.username())).isPresent());
    }

    private boolean hasAssigneeOrCandidate(Task task) {
        return StringUtils.hasText(task.getAssignee()) || tasks.getIdentityLinksForTask(task.getId()).stream()
                .anyMatch(link -> IdentityLinkType.CANDIDATE.equals(link.getType())
                        && (StringUtils.hasText(link.getUserId()) || StringUtils.hasText(link.getGroupId())));
    }

    private void recordReturn(Task source, Task next, Candidate selected, String comment, String operator) {
        var log = new ProcessOperationLog();
        log.setProcessInstanceId(source.getProcessInstanceId());
        log.setTaskId(source.getId());
        log.setOperationType(OPERATION);
        log.setOperatorId(operator);
        log.setOperatorName(identities.getDisplayName(operator));
        log.setOperationComment(comment);
        log.setOperationTime(LocalDateTime.now());
        log.setCreatedAt(log.getOperationTime());
        try {
            log.setNewValue(json.writeValueAsString(new ReturnHistory(selected.target().activityInstanceId(),
                    selected.target().nodeId(), next.getId(), selected.invalidatedActivityIds())));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法记录回退影响的活动", error);
        }
        if (logs.insert(log) != 1) throw new IllegalStateException("流程回退记录写入失败");
    }

    private ProcessOperationException changed(String taskId) {
        return new ProcessOperationException("TASK_STATE_CHANGED", "任务或回退目标已变化，请刷新候选", taskId);
    }
}
