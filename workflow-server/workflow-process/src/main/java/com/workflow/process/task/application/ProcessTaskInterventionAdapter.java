package com.workflow.process.task.application;

import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.port.process.model.task.AddSignType;
import com.workflow.port.process.model.task.SkipTaskCommand;
import com.workflow.port.process.model.task.TaskInterventionModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 将 Java 调用交给各任务干预服务，统一使用运行时状态、锁和幂等规则。 */
@Component
@RequiredArgsConstructor
public class ProcessTaskInterventionAdapter {
    private final TaskHistoryReturnService history;
    private final TaskAddSignService addSign;
    private final TaskSkipService skips;

    public ProcessCommandReceipt skip(SkipTaskCommand command) {
        return skips.skip(command);
    }

    /** 执行后台跳过并登记调用方提供的审计操作人。 */
    public ProcessCommandReceipt skip(SkipTaskCommand command, String operatorId) {
        return skips.skip(command, operatorId);
    }

    public List<HistoryReturnTarget> listHistoryReturnTargets(String taskId) {
        return history.targetsForPort(taskId).stream().map(target -> new HistoryReturnTarget(target.activityInstanceId(),
                target.nodeId(), target.nodeName(), target.resubmit())).toList();
    }

    public HistoryReturnResult returnToHistory(HistoryReturnCommand command) {
        var form = command.formReference();
        var result = history.executeForPort(command.taskId(), new TaskHistoryReturnService.Request(
                command.meta().requestId(), command.targetActivityInstanceId(), command.comment(),
                form == null ? null : form.formId(), form == null ? null : form.configHash()));
        return new HistoryReturnResult(result.processInstanceId(), result.targetNodeId(), result.requestId());
    }

    public AddSignPreview previewAddSign(String taskId, AddSignType type, List<String> userIds) {
        return addSign.previewForPort(taskId, userIds, type == null ? null : type.name());
    }
}
