package com.workflow.process.task.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.*;
import com.workflow.contracts.process.task.model.*;
import com.workflow.contracts.process.task.port.ProcessTaskCommandPort;
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
 * 公共任务命令的唯一编排入口。授权、发布表单、加签分流与原有任务核心共享；
 * 操作结果和回执在同一事务内保存，失败不会留下已成功的回执。
 */
@Service
@RequiredArgsConstructor
public class ProcessTaskCommandAdapter implements ProcessTaskCommandPort {
    private final ProcessCommandExecutor commands;
    private final ProcessInvocationAccess access;
    private final org.flowable.engine.TaskService tasks;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final RepositoryService repository;
    private final TaskActionService actions;
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
            formActions.requireBuiltInMutationAction(form, "submitApproval");
            if ("resubmit".equals(action)) requireResubmit(request.getTaskId());
            return completeWork(request.getTaskId(), action, request.getTransferTo(), submission);
        });
    }

    @Override
    public TaskCommandResult claim(ClaimTaskCommand command) {
        return taskCommand(command.meta(), "TASK_CLAIM", command, command.taskId(), () -> {
            Task task = requireEngineTask(command.taskId());
            boolean changed = task.getAssignee() == null || task.getAssignee().isBlank();
            actions.claimTask(task.getId());
            return outcome(task, task.getId(), "TODO", changed);
        });
    }

    @Override
    public TaskCommandResult complete(CompleteTaskCommand command) {
        return taskCommand(command.meta(), "TASK_COMPLETE", command, command.taskId(),
                () -> {
                    String action = outcomes.normalizeAction(command.actionCode());
                    if (Set.of("transfer", "resubmit").contains(action)) throw denied(command.taskId(),
                            "转办和重提需要使用对应的专用命令");
                    return completeWork(command.taskId(), action, null, command.submission());
                });
    }

    @Override
    public TaskCommandResult transfer(TransferTaskCommand command) {
        return taskCommand(command.meta(), "TASK_TRANSFER", command, command.taskId(),
                () -> completeWork(command.taskId(), "transfer", command.targetUserId(), command.submission()));
    }

    /**
     * 退回仍走已部署 BPMN 的驳回连线，不越过监听器或多实例汇聚直接移动执行。
     * targetNodeId 是对实际落点的约束：仅允许节点策略列出的目标，实际未到达则整笔回滚。
     * 异步、加签及未汇聚的会签应使用普通 reject，不能保证立即退到指定节点。
     */
    @Override
    public TaskCommandResult returnToNode(ReturnTaskCommand command) {
        return taskCommand(command.meta(), "TASK_RETURN", command, command.taskId(), () -> {
            Task source = requireEngineTask(command.taskId());
            actions.requireTaskAccess(command.taskId());
            var allowed = decisions.availableActions(command.taskId()).get("reject");
            if (allowed == null || !allowed.allowed()
                    || !allowed.rule().allowedRejectTargets().contains(command.targetNodeId())
                    || addSigns.isAddSignSourceTask(command.taskId())) {
                throw denied(command.taskId(), "当前节点不允许退回到指定目标");
            }
            Set<String> previous = new HashSet<>(tasks.createTaskQuery().processInstanceId(source.getProcessInstanceId())
                    .list().stream().map(Task::getId).toList());
            var result = completeWork(command.taskId(), "reject", null, command.submission(), command.targetNodeId());
            // 并行分支可能早已有同名节点；只能以本次新生成的目标待办证明退回已到达。
            if (tasks.createTaskQuery().processInstanceId(source.getProcessInstanceId())
                    .taskDefinitionKey(command.targetNodeId()).list().stream().noneMatch(task -> !previous.contains(task.getId()))) {
                throw denied(command.taskId(), "发布流程的驳回路径未到达指定节点，操作已回滚");
            }
            return result;
        });
    }

    /** 重提限于发起人持有的退回待办，复用表单、下一审批人和实体同步路径。 */
    @Override
    public TaskCommandResult resubmit(ResubmitTaskCommand command) {
        return taskCommand(command.meta(), "TASK_RESUBMIT", command, command.taskId(), () -> {
            requireResubmit(command.taskId());
            return completeWork(command.taskId(), "resubmit", null, command.submission());
        });
    }

    /** 发起人身份与退回状态必须同时满足；不允许把任意待办当作重提入口。 */
    private void requireResubmit(String taskId) {
            Task source = requireEngineTask(taskId);
            var actor = access.requireActor();
            var instance = history.createHistoricProcessInstanceQuery()
                    .processInstanceId(source.getProcessInstanceId()).singleResult();
            String starter = instance == null ? null : instance.getStartUserId();
            if (starter == null || !starter.equals(actor.userId()) && !starter.equals(actor.username())) {
                throw new ProcessOperationException("PROCESS_ACCESS_DENIED", "只有发起人可以重新提交", taskId);
            }
            Object approved = runtime.getVariable(source.getProcessInstanceId(), "approved");
            if (!Boolean.TRUE.equals(runtime.getVariable(source.getProcessInstanceId(), "_rejected_"))
                    && !"reject".equals(approved)) throw denied(taskId, "当前任务不是退回重提状态");
            if (addSigns.isAddSignSourceTask(taskId)) throw denied(taskId, "加签编排中不能重新提交");
    }

    @Override
    public AddSignResult addSign(AddSignCommand command) {
        TaskAddSignRequest request = new TaskAddSignRequest();
        request.setType(command.type().name()); request.setUserIds(command.userIds()); request.setComment(command.comment());
        var execution = addSignExecution(command.meta(), command, command.taskId(), request);
        return new AddSignResult(execution.receipt(), execution.value().id(), execution.value().taskIds());
    }

    /** 兼容页面的人员预览字段和完成策略，执行路径与 Java 加签完全相同。 */
    public Map<String, Object> addSignHttp(String taskId, TaskAddSignRequest request) {
        return addSignExecution(ProcessHttpCommands.meta(request.getRequestId()), Map.of("taskId", taskId, "request", request), taskId, request).value().response();
    }

    private ProcessCommandExecutor.Execution<AddSignPayload> addSignExecution(
            CommandMeta meta, Object command, String taskId, TaskAddSignRequest request) {
        access.requireActor();
        return ProcessCommandFailures.translate(taskId, () -> commands.execute(
                meta, "TASK_ADD_SIGN", command, AddSignPayload.class,
                () -> TaskCommandScope.run(taskId, () -> {
                    Task source = requireEngineTask(taskId);
                    var result = addSigns.addSign(taskId, request);
                    var ids = ((List<?>) result.get("generatedTaskIds")).stream().map(String::valueOf).toList();
                    return new ProcessCommandExecutor.Outcome<>(source.getProcessInstanceId(), taskId, true,
                            new AddSignPayload(String.valueOf(result.get("addSignId")), ids, result));
                })));
    }

    @Override
    public TaskCommandResult cancelAddSign(CancelAddSignCommand command) {
        return taskCommand(command.meta(), "TASK_CANCEL_ADD_SIGN", command, command.addSignId(), () -> {
            var sign = addSignRecords.selectById(command.addSignId());
            if (sign == null) throw new ProcessOperationException("TASK_NOT_FOUND", "加签编排不存在", command.addSignId());
            return TaskCommandScope.run(sign.getSourceTaskId(), () -> {
                addSigns.cancel(command.addSignId(), command.reason());
                return new ProcessCommandExecutor.Outcome<>(sign.getProcessInstanceId(), sign.getSourceTaskId(), true,
                        new TaskPayload("ADD_SIGN_CANCELLED", lifecycle(sign.getProcessInstanceId())));
            });
        });
    }

    @Override
    public ManualCcResult addCc(ManualCcCommand command) {
        access.requireActor();
        var execution = ProcessCommandFailures.translate(command.taskId(), () -> commands.execute(
                command.meta(), "TASK_CC", command, Integer.class,
                () -> TaskCommandScope.run(command.taskId(), () -> {
                    Task source = requireEngineTask(command.taskId());
                    TaskCcRequest request = new TaskCcRequest();
                    request.setUserIds(command.userIds());
                    request.setComment(command.comment());
                    int created = operations.manualCc(command.taskId(), request);
                    return new ProcessCommandExecutor.Outcome<>(source.getProcessInstanceId(), command.taskId(), created > 0, created);
                })));
        return new ManualCcResult(execution.receipt(), execution.value());
    }

    /**
     * 普通任务与加签共享这一分流顺序。表单按钮在任何 early return 前校验；
     * 子任务没有独立实体写入，源任务延后办理时由原加签服务保存表单并在最终收口校验。
     */
    private ProcessCommandExecutor.Outcome<TaskPayload> completeWork(
            String taskId, String action, String transferTo, TaskSubmission submission) {
        return completeWork(taskId, action, transferTo, submission, null);
    }

    private ProcessCommandExecutor.Outcome<TaskPayload> completeWork(
            String taskId, String action, String transferTo, TaskSubmission submission, String returnTargetNodeId) {
        boolean local = addSigns.isAddSignTask(taskId);
        Task source;
        if (local) source = localAccess.requireCurrentUserAccess(taskId, null).sourceTask();
        else { source = requireEngineTask(taskId); actions.requireTaskAccess(taskId); }
        forms.require(taskId, source, submission.formReference());
        String normalized = outcomes.normalizeAction(action);
        requirePublishedAction(source, normalized);
        boolean selected = submission.nextApprovers() != null && !submission.nextApprovers().nodes().isEmpty();
        if ((local || addSigns.isAddSignSourceTask(taskId) || "transfer".equals(normalized)) && selected) {
            throw new IllegalArgumentException("加签或转办不能指定下一节点审批人");
        }
        if (local) {
            if (!Set.of("approve", "reject").contains(normalized)) throw denied(taskId, "加签子任务只支持通过或驳回");
            // 最后一个子任务可能同步推进源任务；两者都纳入防重入，阻止源任务监听器递归办理。
            TaskCommandScope.run(source.getId(), () -> {
                addSigns.completeAddSignTask(taskId, normalized, submission.comment());
                return null;
            });
            return outcome(source, taskId, "ADD_SIGN_COMPLETED", true);
        }
        String actor = actorKey();
        if (addSigns.handleSourceCompletion(taskId, actor, normalized, submission.comment(),
                submission.actionLabel(), submission.formData())) {
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
        if (returnTargetNodeId == null) {
            actions.completeTask(taskId, actor, normalized, submission.comment(), transferTo,
                    submission.actionLabel(), submission.formData(), next == null ? null : next.scopeKey(), selections);
        } else {
            actions.completeTaskWithReturnTarget(taskId, actor, submission.comment(), submission.actionLabel(),
                    submission.formData(), next == null ? null : next.scopeKey(), selections, returnTargetNodeId);
        }
        return outcome(source, taskId, "transfer".equals(normalized) ? "TRANSFERRED" : "COMPLETED", true);
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
        access.requireActor();
        var execution = ProcessCommandFailures.translate(taskId, () -> commands.execute(meta, type, command,
                TaskPayload.class, () -> TaskCommandScope.run(taskId, work)));
        return new TaskCommandResult(execution.receipt(), execution.value().status(), execution.value().lifecycle());
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
