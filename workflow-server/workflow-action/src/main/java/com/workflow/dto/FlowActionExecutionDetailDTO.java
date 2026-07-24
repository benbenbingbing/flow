package com.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程动作执行详情 DTO。
 *
 * <p>面向超级管理员执行日志展示，包含执行记录基本信息、触发上下文、解析参数、
 * 执行结果与执行轨迹。</p>
 */
@Data
public class FlowActionExecutionDetailDTO {

    /** 执行记录 ID */
    private String id;
    /** 动作配置 ID */
    private String actionId;
    /** 动作名称 */
    private String actionName;
    /** 处理器 Bean 名称 */
    private String handlerName;
    /** 处理器中文展示名 */
    private String handlerDisplayName;
    /** 所属流程发布版本 ID */
    private String versionId;
    /** 流程实例 ID */
    private String processInstanceId;
    /** Flowable 流程定义 ID */
    private String processDefinitionId;
    /** Flowable 执行实例 ID */
    private String executionId;
    /** 任务 ID（任务级动作） */
    private String taskId;
    /** 实体编码 */
    private String entityCode;
    /** 作用域类型 */
    private String scopeType;
    /** 绑定的 BPMN 元素 ID */
    private String elementId;
    /** 触发时机编码 */
    private String triggerTiming;
    /** 幂等键 */
    private String idempotencyKey;
    /** 执行状态 */
    private String status;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetries;
    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;
    /** 错误信息 */
    private String errorMessage;
    /** 错误堆栈 */
    private String errorStack;
    /** 执行耗时（毫秒） */
    private Long durationMs;
    /** 开始执行时间 */
    private LocalDateTime startedAt;
    /** 完成时间 */
    private LocalDateTime finishedAt;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 触发上下文（payload 解析后的 map，已脱敏） */
    private Map<String, Object> triggerContext;
    /** 解析后的业务参数（已脱敏） */
    private Map<String, Object> resolvedParams;
    /** 处理器执行结果（已脱敏） */
    private Object result;
    /** 执行轨迹列表 */
    private List<Map<String, Object>> executionTrace;
}
