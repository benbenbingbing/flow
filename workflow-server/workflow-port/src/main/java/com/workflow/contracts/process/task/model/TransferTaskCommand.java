package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 转办与本地待办、日志同步属于同一事务。
 * @param meta 一次业务意图的标识
 * @param taskId 待转办任务
 * @param targetUserId 接收任务的用户
 * @param submission 说明和允许修改的表单补丁
 */
public record TransferTaskCommand(
        CommandMeta meta,
        String taskId,
        String targetUserId,
        TaskSubmission submission) {
    public TransferTaskCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        targetUserId = ProcessValues.required(targetUserId, "targetUserId");
        submission = submission == null ? TaskSubmission.comment(null) : submission;

    }

}
