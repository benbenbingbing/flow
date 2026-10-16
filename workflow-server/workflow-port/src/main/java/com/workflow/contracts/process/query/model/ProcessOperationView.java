package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 流程操作时间线的一项，不携带实体字段值。
 * @param operationId 日志唯一标识
 * @param requestId 关联公共命令请求，旧日志可空
 * @param instanceId 所属流程实例
 * @param taskId 目标任务，可空
 * @param action 稳定操作编码
 * @param operatorId 实际操作人
 * @param time 操作时刻
 * @param comment 操作说明
 */
public record ProcessOperationView(
        String operationId,
        String requestId,
        String instanceId,
        String taskId,
        String action,
        String operatorId,
        Instant time,
        String comment) {

}
