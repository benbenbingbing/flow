package com.workflow.process.task.application;

import com.workflow.port.process.ProcessTaskPort;
import com.workflow.port.process.error.ProcessOperationException;
import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.port.process.model.ProcessPage;
import com.workflow.port.process.model.ProcessValues;
import com.workflow.core.error.ForbiddenException;
import com.workflow.port.process.model.query.*;
import com.workflow.port.process.model.task.*;
import com.workflow.port.process.model.task.TaskInterventionModels.AddSignPreview;
import com.workflow.port.process.model.task.TaskInterventionModels.HistoryReturnCommand;
import com.workflow.port.process.model.task.TaskInterventionModels.HistoryReturnResult;
import com.workflow.port.process.model.task.TaskInterventionModels.HistoryReturnTarget;
import com.workflow.process.query.application.ProcessQueryAdapter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 聚合任务查询、办理和干预入口，保留原有任务身份、发布规则与幂等校验。 */
@Component
@RequiredArgsConstructor
public class ProcessTaskAdapter implements ProcessTaskPort {
    private final ProcessQueryAdapter queries;
    private final ProcessTaskCommandAdapter commands;
    private final ProcessTaskInterventionAdapter interventions;
    private final TaskActionService taskActions;
    private final TaskDetailService taskDetails;

    @Override
    public Optional<ProcessTaskView> findActiveTask(String taskId) {
        return queries.findActiveTask(taskId);
    }

    /** 先校验待办身份，之后才解析表单，避免仅凭实例读取权限看到办理信息。 */
    @Override
    public ProcessTaskDetail getTaskDetail(String taskId) {
        ProcessValues.required(taskId, "taskId");
        var task = findActiveTask(taskId)
                .orElseThrow(() -> new ProcessOperationException("TASK_NOT_FOUND", "活动任务不存在", taskId));
        boolean addSign = "ADD_SIGN".equals(task.kind());
        try {
            if (addSign) taskDetails.requireLocalAddSignTaskAccess(taskId);
            else taskActions.requireTaskAccess(taskId);
            var capabilities = getTaskCapabilities(taskId);
            var form = taskDetails.findActiveTaskForm(taskId, addSign);
            return new ProcessTaskDetail(task, queries.requireInstance(task.instanceId()).businessReference(),
                    form == null ? null : new ProcessTaskDetail.FormSummary(form.getEntityFormId(),
                            form.getFormKey(), form.getFormName(), Boolean.TRUE.equals(form.getIsReadonly())),
                    capabilities);
        } catch (ForbiddenException denied) {
            throw new ProcessOperationException("PROCESS_ACCESS_DENIED", denied.getMessage(), taskId, denied);
        }
    }

    @Override
    public List<ProcessTaskView> listActiveTasks(String processInstanceId) {
        return queries.listActiveTasks(processInstanceId);
    }

    @Override
    public ProcessPage<ProcessTaskView> pageTasks(ProcessTaskQuery query) {
        return queries.pageTasks(query);
    }

    @Override
    public TaskCapabilities getTaskCapabilities(String taskId) {
        return queries.getTaskCapabilities(taskId);
    }

    @Override
    public TaskCommandResult claim(ClaimTaskCommand command) {
        return commands.claim(command);
    }

    @Override
    public TaskCommandResult complete(CompleteTaskCommand command) {
        return commands.complete(command);
    }

    @Override
    public ProcessCommandReceipt skip(SkipTaskCommand command) {
        return interventions.skip(command);
    }

    @Override
    public ProcessCommandReceipt skip(SkipTaskCommand command, String operatorId) {
        return interventions.skip(command, operatorId);
    }

    @Override
    public TaskCommandResult transfer(TransferTaskCommand command) {
        return commands.transfer(command);
    }

    @Override
    public TaskCommandResult returnToNode(ReturnTaskCommand command) {
        return commands.returnToNode(command);
    }

    @Override
    public TaskCommandResult resubmit(ResubmitTaskCommand command) {
        return commands.resubmit(command);
    }

    @Override
    public AddSignResult addSign(AddSignCommand command) {
        return commands.addSign(command);
    }

    @Override
    public TaskCommandResult cancelAddSign(CancelAddSignCommand command) {
        return commands.cancelAddSign(command);
    }

    @Override
    public ManualCcResult addCc(ManualCcCommand command) {
        return commands.addCc(command);
    }

    @Override
    public List<HistoryReturnTarget> listHistoryReturnTargets(String taskId) {
        return interventions.listHistoryReturnTargets(taskId);
    }

    @Override
    public HistoryReturnResult returnToHistory(HistoryReturnCommand command) {
        return interventions.returnToHistory(command);
    }

    @Override
    public AddSignPreview previewAddSign(String taskId, AddSignType type, List<String> userIds) {
        return interventions.previewAddSign(taskId, type, userIds);
    }
}
