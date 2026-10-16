package com.workflow.contracts.process.instance.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 触发正在等待的接收任务；定位和变量写入与推进在同一事务内。
 * @param meta 一次业务意图的标识
 * @param processInstanceId 目标实例
 * @param executionId 精确执行分支，可空
 * @param activityId 等待节点，未提供执行 ID 时必填
 * @param messageRef 节点期望的消息标识
 * @param variables 本次推进所需的业务变量
 */
public record TriggerReceiveTaskCommand(
        CommandMeta meta,
        String processInstanceId,
        String executionId,
        String activityId,
        String messageRef,
        Map<String,Object> variables) {
    public TriggerReceiveTaskCommand {
        Objects.requireNonNull(meta, "meta");
        processInstanceId = ProcessValues.required(processInstanceId, "processInstanceId");
        if ((executionId == null || executionId.isBlank()) && (activityId == null || activityId.isBlank())) throw new IllegalArgumentException("executionId 与 activityId 至少填写一个");
        variables = ProcessValues.snapshot(variables);

    }

}
