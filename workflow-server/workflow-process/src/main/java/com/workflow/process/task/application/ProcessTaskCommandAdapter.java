package com.workflow.process.task.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.process.error.ProcessOperationException;
import com.workflow.port.process.model.*;
import com.workflow.port.process.model.task.*;
import com.workflow.process.cc.api.request.TaskCcRequest;
import com.workflow.process.command.application.*;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import com.workflow.process.task.api.request.NextApproverSelectionRequest;
import com.workflow.process.task.api.request.TaskAddSignRequest;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.*;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Supplier;

/**
 * 公共任务命令的唯一编排入口。页面授权与调用方授权使用独立入口，共享发布表单、加签分流和任务核心；
 * 操作结果和回执在同一事务内保存，失败不会留下已成功的回执。
 */
@Service
@RequiredArgsConstructor
public class ProcessTaskCommandAdapter {
    private final ProcessCommandExecutor commands;
    private final ProcessInvocationAccess access;
    private final org.flowable.engine.TaskService tasks;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final RepositoryService repository;
    private final TaskActionService actions;
    private final TaskIdentityAccessService identities;
    private final TaskAddSignService addSigns;
    private final LocalAddSignTaskAccessService localAccess;
    private final TaskSubmissionGuard forms;
    private final TaskOperationApplicationService operations;
    private final ProcessTaskAddSignMapper addSignRecords;
    private final NodeOperationDecisionService decisions;
    private final MultiInstanceOutcomeService outcomes;
    private final ObjectMapper json;
    private final com.workflow.entity.form.application.EntityFormActionService formActions;

    /**
     * 页面兼容入口保留客户端的表单/记录联合校验，并复用完整任务命令编排。
     * 校验放在幂等事务工作内，成功请求即使任务已结束也可以重放。
     * 旧客户端没有 requestId 时每次生成新意图；需可靠重试的客户端应稳定传入 requestId。
     */
    public TaskCommandResult completeHttp(com.workflow.process.task.api.request.TaskCompleteRequest request) {
        String action = outcomes.normalizeAction(request.getAction());
        var next = request.getNextApproverSelections() == null ? null : new NextApproverSelection(
                request.getNextApprovalScopeKey(), request.getNextApproverSelections().stream()
                .map(item -> new NodeApproverSelection(item.getNodeId(), item.getUserKeys())).toList());
        var submission = new TaskSubmission(request.getComment(), request.getActionLabel(), request.getFormData(),
                new TaskFormReference(request.getFormId(), request.getFormConfigHash()), next);
        var meta = new CommandMeta(request.getRequestId() == null || request.getRequestId().isBlank()
                ? UUID.randomUUID().toString() : request.getRequestId());
        // 记录摘要包含页面声明，不能让同一请求键在重试时偷换业务记录或表单。
        return taskCommand(meta, "TASK_HTTP_COMPLETE", request, request.getTaskId(), () -> {
            var form = new com.workflow.entity.form.api.request.FormActionResolveRequest();
            form.setTaskId(request.getTaskId()); form.setMode("approve");
            form.setFormId(request.getFormId()); form.setConfigHash(request.getFormConfigHash());
            form.setEntityCode(request.getEntityCode()); form.setRecordId(request.getRecordId());
            form.setListKey(request.getListKey());
            Task resubmit = "resubmit".equals(action) ? requireResubmit(request.getTaskId(), false) : null;
            if (resubmit == null || !isHistoryResubmission(resubmit.getId())
                    || !isUnboundTask(resubmit, submission.formReference())) {
                formActions.requireBuiltInMutationAction(form, "submitApproval");
            }
            return completeWork(request.getTaskId(), action, request.getTransferTo(), submission, null, false);
        });
    }

    public TaskCommandResult claim(ClaimTaskCommand command) {
        return claim(command, false);
    }

    /** 页面入口由当前待办身份授权，并复用认领事务。 */
    public TaskCommandResult claimForUser(ClaimTaskCommand command) {
        return claim(command, true);
    }

    private TaskCommandResult claim(ClaimTaskCommand command, boolean checkAccess) {
        return taskCommand(command.meta(), "TASK_CLAIM", command, command.taskId(), !checkAccess, () -> {
            Task task = requireEngineTask(command.taskId());
            boolean changed = task.getAssignee() == null || task.getAssignee().isBlank();
            if (checkAccess) actions.claimTask(task.getId());
            else actions.claimTaskForPort(task.getId());
            return outcome(task, task.getId(), "TODO", changed);
        });
    }

    public TaskCommandResult complete(CompleteTaskCommand command) {
        return complete(command, true);
    }

    /** 用户入口保留当前任务办理及表单权限。 */
    public TaskCommandResult completeForUser(CompleteTaskCommand command) {
        return complete(command, false);
    }

    private TaskCommandResult complete(CompleteTaskCommand command, boolean portCall) {
        return taskCommand(command.meta(), "TASK_COMPLETE", command, command.taskId(), portCall,
                () -> {
                    String action = outcomes.normalizeAction(command.actionCode());
                    if (Set.of("transfer", "resubmit").contains(action)) throw denied(command.taskId(),
                            "转办和重提需要使用对应的专用命令");
                    return completeWork(command.taskId(), action, null, command.submission(), null, portCall);
                });
    }

    public TaskCommandResult transfer(TransferTaskCommand command) {
        return transfer(command, true);
    }

    /** 用户入口按待办检查转办人权限。 */
    public TaskCommandResult transferForUser(TransferTaskCommand command) {
        return transfer(command, false);
    }

    private TaskCommandResult transfer(TransferTaskCommand command, boolean portCall) {
        return taskCommand(command.meta(), "TASK_TRANSFER", command, command.taskId(), portCall,
                () -> completeWork(command.taskId(), "transfer", command.targetUserId(), command.submission(), null, portCall));
    }

    /**
     * 退回仍走已部署 BPMN 的驳回连线，不越过监听器或多实例汇聚直接移动执行。
     * targetNodeId 是对实际落点的约束：仅允许节点策略列出的目标，实际未到达则整笔回滚。
     * 异步、加签及未汇聚的会签应使用普通 reject，不能保证立即退到指定节点。
     */
    public TaskCommandResult returnToNode(ReturnTaskCommand command) {
        return returnToNode(command, true);
    }

    /** 页面退回需同时满足用户办理身份和发布节点配置。 */
    public TaskCommandResult returnToNodeForUser(ReturnTaskCommand command) {
        return returnToNode(command, false);
    }

    private TaskCommandResult returnToNode(ReturnTaskCommand command, boolean portCall) {
        return taskCommand(command.meta(), "TASK_RETURN", command, command.taskId(), portCall, () -> {
            Task source = requireEngineTask(command.taskId());
            if (!portCall) actions.requireTaskAccess(command.taskId());
            var decisionsByAction = portCall ? decisions.availableActionsForPort(command.taskId(), access.requireActor().userId())
                    : decisions.availableActions(command.taskId());
            var allowed = decisionsByAction.get("reject");
            if (allowed == null || !allowed.allowed()
                    || !allowed.rule().allowedRejectTargets().contains(command.targetNodeId())
                    || addSigns.isAddSignSourceTask(command.taskId())) {
                throw denied(command.taskId(), "当前节点不允许退回到指定目标");
            }
            Set<String> previous = new HashSet<>(tasks.createTaskQuery().processInstanceId(source.getProcessInstanceId())
                    .list().stream().map(Task::getId).toList());
            var result = completeWork(command.taskId(), "reject", null, command.submission(), command.targetNodeId(), portCall);
            // 并行分支可能早已有同名节点；只能以本次新生成的目标待办证明退回已到达。
            if (tasks.createTaskQuery().processInstanceId(source.getProcessInstanceId())
                    .taskDefinitionKey(command.targetNodeId()).list().stream().noneMatch(task -> !previous.contains(task.getId()))) {
                throw denied(command.taskId(), "发布流程的驳回路径未到达指定节点，操作已回滚");
            }
            return result;
        });
    }

    /** 重提限于发起人持有的退回待办，复用表单、下一审批人和实体同步路径。 */
    public TaskCommandResult resubmit(ResubmitTaskCommand command) {
        return resubmit(command, true);
    }

    /** 页面重提仍由流程发起人办理。 */
    public TaskCommandResult resubmitForUser(ResubmitTaskCommand command) {
        return resubmit(command, false);
    }

    private TaskCommandResult resubmit(ResubmitTaskCommand command, boolean portCall) {
        return taskCommand(command.meta(), "TASK_RESUBMIT", command, command.taskId(), portCall, () -> {
            requireResubmit(command.taskId(), portCall);
            return completeWork(command.taskId(), "resubmit", null, command.submission(), null, portCall);
        });
    }

    /** 发起人身份与退回状态必须同时满足；不允许把任意待办当作重提入口。 */
    private Task requireResubmit(String taskId, boolean portCall) {
            Task source = requireEngineTask(taskId);
            var actor = access.requireActor();
            var instance = history.createHistoricProcessInstanceQuery()
                    .processInstanceId(source.getProcessInstanceId()).singleResult();
            String starter = instance == null ? null : instance.getStartUserId();
            if (portCall && !identities.isAssignedOrCandidate(source, starter)) {
                throw denied(taskId, "当前任务不是发起人的退回待办");
            }
            if (!portCall && (starter == null || !starter.equals(actor.userId()) && !starter.equals(actor.username()))) {
                throw new ProcessOperationException("PROCESS_ACCESS_DENIED", "只有发起人可以重新提交", taskId);
            }
            Object approved = runtime.getVariable(source.getProcessInstanceId(), "approved");
            if (!isHistoryResubmission(taskId)
                    && !Boolean.TRUE.equals(runtime.getVariable(source.getProcessInstanceId(), "_rejected_"))
                    && !"reject".equals(approved)) throw denied(taskId, "当前任务不是退回重提状态");
            if (addSigns.isAddSignSourceTask(taskId)) throw denied(taskId, "加签编排中不能重新提交");
            return source;
    }

    public AddSignResult addSign(AddSignCommand command) {
        TaskAddSignRequest request = new TaskAddSignRequest();
        request.setType(command.type().name()); request.setUserIds(command.userIds()); request.setComment(command.comment());
        var execution = addSignExecution(command.meta(), command, command.taskId(), request, true);
        return new AddSignResult(execution.receipt(), execution.value().id(), execution.value().taskIds());
    }

    /** 兼容页面的人员预览字段和完成策略，执行路径与 Java 加签完全相同。 */
    public Map<String, Object> addSignHttp(String taskId, TaskAddSignRequest request) {
        return addSignExecution(ProcessHttpCommands.meta(request.getRequestId()), Map.of("taskId", taskId, "request", request),
                taskId, request, false).value().response();
    }

    private ProcessCommandExecutor.Execution<AddSignPayload> addSignExecution(
            CommandMeta meta, Object command, String taskId, TaskAddSignRequest request, boolean portCall) {
        String operator = actorKey();
        return ProcessCommandFailures.translate(taskId, () -> execute(
                meta, "TASK_ADD_SIGN", command, AddSignPayload.class, portCall,
                () -> TaskCommandScope.run(taskId, () -> {
                    Task source = requireEngineTask(taskId);
                    var result = portCall ? addSigns.addSignForPort(taskId, request, operator) : addSigns.addSign(taskId, request);
                    var ids = ((List<?>) result.get("generatedTaskIds")).stream().map(String::valueOf).toList();
                    return new ProcessCommandExecutor.Outcome<>(source.getProcessInstanceId(), taskId, true,
                            new AddSignPayload(String.valueOf(result.get("addSignId")), ids, result));
                })));
    }

    public TaskCommandResult cancelAddSign(CancelAddSignCommand command) {
        return cancelAddSign(command, true);
    }

    /** 页面撤销由原加签发起人的身份授权。 */
    public TaskCommandResult cancelAddSignForUser(CancelAddSignCommand command) {
        return cancelAddSign(command, false);
    }

    private TaskCommandResult cancelAddSign(CancelAddSignCommand command, boolean portCall) {
        return taskCommand(command.meta(), "TASK_CANCEL_ADD_SIGN", command, command.addSignId(), portCall, () -> {
            var sign = addSignRecords.selectById(command.addSignId());
            if (sign == null) throw new ProcessOperationException("TASK_NOT_FOUND", "加签编排不存在", command.addSignId());
            return TaskCommandScope.run(sign.getSourceTaskId(), () -> {
                if (portCall) addSigns.cancelForPort(command.addSignId(), command.reason(), actorKey());
                else addSigns.cancel(command.addSignId(), command.reason());
                return new ProcessCommandExecutor.Outcome<>(sign.getProcessInstanceId(), sign.getSourceTaskId(), true,
                        new TaskPayload("ADD_SIGN_CANCELLED", lifecycle(sign.getProcessInstanceId())));
            });
        });
    }

    public ManualCcResult addCc(ManualCcCommand command) {
        return addCc(command, true);
    }

    /** 页面知会沿用任务办理身份权限和原回执范围。 */
    public ManualCcResult addCcForUser(ManualCcCommand command) {
        return addCc(command, false);
    }

    private ManualCcResult addCc(ManualCcCommand command, boolean portCall) {
        String operator = actorKey();
        var execution = ProcessCommandFailures.translate(command.taskId(), () -> execute(
                command.meta(), "TASK_CC", command, Integer.class, portCall,
                () -> TaskCommandScope.run(command.taskId(), () -> {
                    Task source = requireEngineTask(command.taskId());
                    TaskCcRequest request = new TaskCcRequest();
                    request.setUserIds(command.userIds());
                    request.setComment(command.comment());
                    int created = portCall ? operations.manualCcForPort(command.taskId(), request, operator)
                            : operations.manualCc(command.taskId(), request);
                    return new ProcessCommandExecutor.Outcome<>(source.getProcessInstanceId(), command.taskId(), created > 0, created);
                })));
        return new ManualCcResult(execution.receipt(), execution.value());
    }

    /**
     * 普通任务与加签共享这一分流顺序。表单按钮在任何 early return 前校验；
     * 子任务没有独立实体写入，源任务延后办理时由原加签服务保存表单并在最终收口校验。
     */
    private ProcessCommandExecutor.Outcome<TaskPayload> completeWork(
            String taskId, String action, String transferTo, TaskSubmission submission, String returnTargetNodeId,
            boolean portCall) {
        boolean local = addSigns.isAddSignTask(taskId);
        Task source;
        if (local) {
            source = (portCall ? localAccess.requireActiveForPort(taskId, null)
                    : localAccess.requireCurrentUserAccess(taskId, null)).sourceTask();
            if (portCall && source.isSuspended()) throw new ProcessOperationException(
                    "PROCESS_STATE_CHANGED", "任务所属流程已挂起", taskId);
        } else {
            source = requireEngineTask(taskId);
            if (!portCall) actions.requireTaskAccess(taskId);
        }
        String normalized = outcomes.normalizeAction(action);
        // 发起人重提使用专用的审批结果，不能从通用完成接口转交或伪造其他办理结果。
        boolean historyResubmit = !local && isHistoryResubmission(taskId);
        if (historyResubmit && !"resubmit".equals(normalized)) {
            throw denied(taskId, "当前待办需由发起人重新提交");
        }
        if (!historyResubmit || !isUnboundTask(source, submission.formReference())) {
            if (portCall) forms.requireForPort(taskId, source, submission.formReference());
            else forms.require(taskId, source, submission.formReference());
        }
        requirePublishedAction(source, normalized);
        boolean selected = submission.nextApprovers() != null && !submission.nextApprovers().nodes().isEmpty();
        if ((local || addSigns.isAddSignSourceTask(taskId) || "transfer".equals(normalized)) && selected) {
            throw new IllegalArgumentException("加签或转办不能指定下一节点审批人");
        }
        String actor = actorKey();
        if (local) {
            if (!Set.of("approve", "reject").contains(normalized)) throw denied(taskId, "加签子任务只支持通过或驳回");
            // 最后一个子任务可能同步推进源任务；两者都纳入防重入，阻止源任务监听器递归办理。
            TaskCommandScope.run(source.getId(), () -> {
                if (portCall) addSigns.completeAddSignTaskForPort(taskId, normalized, submission.comment(), actor);
                else addSigns.completeAddSignTask(taskId, normalized, submission.comment());
                return null;
            });
            return outcome(source, taskId, "ADD_SIGN_COMPLETED", true);
        }
        boolean deferred = portCall
                ? addSigns.handleSourceCompletionForPort(taskId, actor, normalized, submission.comment(),
                        submission.actionLabel(), submission.formData())
                : addSigns.handleSourceCompletion(taskId, actor, normalized, submission.comment(),
                        submission.actionLabel(), submission.formData());
        if (deferred) {
            // 收口可能因下一审批人仍需手选而恢复源待办。此时编排已经关闭，不能误报仍在等待加签。
            String status = tasks.createTaskQuery().taskId(taskId).count() == 0 ? "COMPLETED"
                    : addSignRecords.countBlockingBySourceTaskId(taskId) > 0 ? "WAITING_ADD_SIGN" : "TODO";
            return outcome(source, taskId, status, true);
        }
        var next = submission.nextApprovers();
        List<NextApproverSelectionRequest> selections = next == null ? null : next.nodes().stream().map(node -> {
            var request = new NextApproverSelectionRequest();
            request.setNodeId(node.nodeId()); request.setUserKeys(node.userKeys()); return request;
        }).toList();
        if (portCall) {
            actions.completeTaskForPort(taskId, actor, normalized, submission.comment(), transferTo,
                    submission.actionLabel(), submission.formData(), next == null ? null : next.scopeKey(), selections, returnTargetNodeId);
        } else if (returnTargetNodeId == null) {
            actions.completeTask(taskId, actor, normalized, submission.comment(), transferTo,
                    submission.actionLabel(), submission.formData(), next == null ? null : next.scopeKey(), selections);
        } else {
            actions.completeTaskWithReturnTarget(taskId, actor, submission.comment(), submission.actionLabel(),
                    submission.formData(), next == null ? null : next.scopeKey(), selections, returnTargetNodeId);
        }
        return outcome(source, taskId, "transfer".equals(normalized) ? "TRANSFERRED" : "COMPLETED", true);
    }

    private boolean isHistoryResubmission(String taskId) {
        return Boolean.TRUE.equals(tasks.getVariableLocal(taskId, TaskHistoryReturnService.RESUBMIT_MARKER));
    }

    /** 纯 BPMN 流程能重提，关联实体或声明表单引用时仍校验发布表单。 */
    private boolean isUnboundTask(Task source, TaskFormReference reference) {
        return (reference == null || reference.formId() == null && reference.configHash() == null)
                && runtime.getVariable(source.getProcessInstanceId(), "entityCode") == null
                && runtime.getVariable(source.getProcessInstanceId(), "entityDataId") == null;
    }

    /** 仅已发布自定义按钮值可作为审批动作；显示文本不参与授权或路由。 */
    private void requirePublishedAction(Task task, String action) {
        if (Set.of("approve", "reject", "transfer", "resubmit").contains(action)) return;
        var model = repository.getBpmnModel(task.getProcessDefinitionId());
        var element = model == null ? null : model.getFlowElement(task.getTaskDefinitionKey());
        String configuration = ConfiguredTaskPropertyReader.read(element, "approvalConfig");
        if (configuration != null) {
            try {
                var config = json.readTree(configuration);
                if (config.path("enabled").asBoolean(true)) {
                    for (var option : config.path("options")) {
                        if (action.equals(option.path("value").asText())) return;
                    }
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                throw new IllegalStateException("已发布审批动作配置无法解析", failure);
            }
        }
        throw denied(task.getId(), "当前发布节点未开放该审批动作");
    }

    /** 回执重放发生在重新读取活动任务之前，任务已经完成也能返回原成功结果。 */
    private TaskCommandResult taskCommand(CommandMeta meta, String type, Object command, String taskId,
                                         Supplier<ProcessCommandExecutor.Outcome<TaskPayload>> work) {
        return taskCommand(meta, type, command, taskId, false, work);
    }

    private TaskCommandResult taskCommand(CommandMeta meta, String type, Object command, String taskId,
            boolean portCall, Supplier<ProcessCommandExecutor.Outcome<TaskPayload>> work) {
        access.requireActor();
        var execution = ProcessCommandFailures.translate(taskId, () -> execute(meta, type, command,
                TaskPayload.class, portCall, () -> TaskCommandScope.run(taskId, work)));
        return new TaskCommandResult(execution.receipt(), execution.value().status(), execution.value().lifecycle());
    }

    private <T> ProcessCommandExecutor.Execution<T> execute(CommandMeta meta, String type, Object command,
            Class<T> resultType, boolean portCall, Supplier<ProcessCommandExecutor.Outcome<T>> work) {
        return portCall ? commands.executeForPort(meta, type, command, resultType, work)
                : commands.execute(meta, type, command, resultType, work);
    }
    private Task requireEngineTask(String taskId) {
        Task task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            boolean completed = history.createHistoricTaskInstanceQuery().taskId(taskId).singleResult() != null;
            throw new ProcessOperationException(completed ? "TASK_ALREADY_COMPLETED" : "TASK_NOT_FOUND",
                    completed ? "任务已被处理" : "任务不存在", taskId);
        }
        if (task.isSuspended()) throw new ProcessOperationException("PROCESS_STATE_CHANGED", "任务所属流程已挂起", taskId);
        return task;
    }
    private String actorKey() {
        var actor = access.requireActor();
        return actor.username() == null || actor.username().isBlank() ? actor.userId() : actor.username();
    }
    private ProcessCommandExecutor.Outcome<TaskPayload> outcome(Task source, String id, String status, boolean changed) {
        return new ProcessCommandExecutor.Outcome<>(source.getProcessInstanceId(), id, changed,
                new TaskPayload(status, lifecycle(source.getProcessInstanceId())));
    }
    private ProcessLifecycle lifecycle(String id) {
        var instance = runtime.createProcessInstanceQuery().processInstanceId(id).singleResult();
        return instance == null ? ProcessLifecycle.ENDED : instance.isSuspended() ? ProcessLifecycle.SUSPENDED : ProcessLifecycle.RUNNING;
    }
    private ProcessOperationException denied(String id, String message) {
        return new ProcessOperationException("TASK_ACTION_NOT_ALLOWED", message, id);
    }
    /** 固定类型持久化，不把引擎对象或任意类名写进回执。 */
    public record TaskPayload(String status, ProcessLifecycle lifecycle) { }
    public record AddSignPayload(String id, List<String> taskIds, Map<String, Object> response) {
        public AddSignPayload { taskIds = List.copyOf(taskIds); response = ProcessValues.snapshot(response); }
    }
}
