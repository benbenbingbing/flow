package com.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接收任务触发请求 DTO。
 *
 * <p>用于触发流程中处于等待状态的 ReceiveTask（接收任务）节点继续向下流转，
 * 可通过执行实例ID或节点ID定位目标，并可携带消息标识与流程变量。</p>
 */
@Data
public class ReceiveTaskTriggerRequest {

    /**
     * 执行实例ID（可选，与 activityId 至少填写一个；指定后直接触发该执行实例）
     */
    private String executionId;

    /**
     * 接收任务节点ID（可选，未提供 executionId 时按节点ID定位执行实例）
     */
    private String activityId;

    /**
     * 消息标识（可选，用于与接收任务配置的期望消息进行校验）
     */
    private String messageRef;

    /**
     * 触发时需要写入的流程变量
     */
    private Map<String, Object> variables = new LinkedHashMap<>();
}
