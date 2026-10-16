package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 平台任务投影，覆盖引擎人工任务和本地加签任务。
 * @param taskId 平台办理入口使用的任务标识
 * @param engineTaskId 真实引擎任务 ID，本地加签为空
 * @param kind ENGINE 或 ADD_SIGN
 * @param instanceId 所属实例
 * @param nodeId 真实源节点编码
 * @param taskName 任务显示名称
 * @param status 平台待办状态
 * @param assigneeId 实际办理人标识
 * @param createTime 创建时刻
 * @param endTime 结束时刻
 * @param canClaim 当前身份可认领，仅供提示，命令仍重验
 */
public record ProcessTaskView(
        String taskId,
        String engineTaskId,
        String kind,
        String instanceId,
        String nodeId,
        String taskName,
        String status,
        String assigneeId,
        Instant createTime,
        Instant endTime,
        boolean canClaim) {

}
