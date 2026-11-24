package com.workflow.port.process.model.task;

import com.workflow.port.process.model.CommandMeta;
import com.workflow.port.process.model.ProcessValues;

import java.util.Objects;

/**
 * 跳过运行中的一次待办。
 * @param meta 本次业务意图的标识，重试须复用原请求 ID
 * @param taskId 活动待办 ID，用于区分同一节点的不同执行
 * @param reason 跳过原因，用于流程操作日志及待办处理记录
 */
public record SkipTaskCommand(CommandMeta meta, String taskId, String reason) {
    public SkipTaskCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        reason = ProcessValues.required(reason, "reason").strip();
        if (reason.length() > 2000) throw new IllegalArgumentException("跳过原因超过 2000 字符");
    }
}
