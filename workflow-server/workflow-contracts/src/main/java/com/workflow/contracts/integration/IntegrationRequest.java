package com.workflow.contracts.integration;

import com.workflow.contracts.entity.list.DataScopePlan;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 集成调用请求。
 * 封装对外部集成连接器的调用输入，包含幂等键、操作类型、参数及运行上下文等信息。
 */
@Value
@Builder
public class IntegrationRequest {

    /** 幂等键，用于防止重复调用 */
    String idempotencyKey;
    /** 操作类型标识 */
    String operation;
    /** 调用参数 */
    Map<String, Object> parameters;
    /** 运行时上下文（来源、配置、用户、租户等） */
    IntegrationRuntimeContext runtimeContext;
    /** 数据范围查询计划 */
    DataScopePlan dataScopePlan;
    /** 权限摘要信息 */
    Map<String, Object> permissionSummary;
}
