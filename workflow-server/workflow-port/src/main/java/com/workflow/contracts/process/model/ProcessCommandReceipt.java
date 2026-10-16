package com.workflow.contracts.process.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 命令执行回执；在外层事务内返回时不代表事务已经提交。
 * @param requestId 本次业务意图的幂等请求 ID
 * @param operationId 持久化操作 ID，用于追踪与审计
 * @param processInstanceId 本次操作所属流程实例
 * @param taskId 任务操作的目标，实例操作可空
 * @param changed 本次业务意图是否改变状态
 * @param replayed 是否返回同一请求的已执行结果
 */
public record ProcessCommandReceipt(
        String requestId,
        String operationId,
        String processInstanceId,
        String taskId,
        boolean changed,
        boolean replayed) {

}
