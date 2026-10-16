package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 流程实例的独立投影；只包含流程信息，不携带实体全文和页面令牌。
 * @param instanceId 运行/历史共用的实例 ID
 * @param processConfigId 平台流程配置 ID
 * @param engineDefinitionId 精确 Flowable 部署定义 ID
 * @param processKey 稳定流程编码
 * @param deploymentVersion 引擎部署版本
 * @param processVersionId 平台发布版本 ID
 * @param businessReference 原业务关联
 * @param startUserId 实际发起人标识
 * @param lifecycle 当前生命周期
 * @param endType 结束类别，未结束为空
 * @param startTime 启动时刻
 * @param endTime 结束时刻
 */
public record ProcessInstanceView(
        String instanceId,
        String processConfigId,
        String engineDefinitionId,
        String processKey,
        Integer deploymentVersion,
        String processVersionId,
        ProcessBusinessReference businessReference,
        String startUserId,
        ProcessLifecycle lifecycle,
        ProcessEndType endType,
        Instant startTime,
        Instant endTime) {

}
