package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 在当前实例退回任务上重提，不创建新流程轮次。
 * @param meta 一次业务意图的标识
 * @param taskId 当前退回任务
 * @param submission 重新提交的信息
 */
public record ResubmitTaskCommand(
        CommandMeta meta,
        String taskId,
        TaskSubmission submission) {
    public ResubmitTaskCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        submission = submission == null ? TaskSubmission.comment(null) : submission;

    }

}
