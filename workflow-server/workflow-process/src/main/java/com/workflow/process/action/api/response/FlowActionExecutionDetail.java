package com.workflow.process.action.api.response;

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
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class FlowActionExecutionDetail {

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
    /** 首次执行为 1，自定义策略用它限制额外重试预算。 */
    private Integer attemptNo;
    /** 最终处理原因；继续、忽略和人工处理仍保留失败事实。 */
    private String terminationReason;
    /** 自定义策略显示身份，不向浏览器返回内部执行快照。 */
    private String failureStrategyCode;
    private String failureStrategyVersion;
    /** NONE/OPEN/RESOLVED/REPLAYING，独立于动作执行成功与否。 */
    private String resolutionStatus;
    /** 同一重放链的根记录，用于串行化人工重放请求。 */
    private String replayRootId;
    /** 发起本次重放的原失败记录，原记录不会被清空。 */
    private String replayOfId;
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
