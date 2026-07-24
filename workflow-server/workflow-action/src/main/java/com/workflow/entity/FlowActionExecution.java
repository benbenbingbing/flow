package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程动作执行记录实体。
 *
 * <p>对应 process_action_execution 表，记录每一次流程动作的执行过程，包括触发上下文、
 * 解析参数、执行结果、执行轨迹、状态流转、重试信息与耗时等，用于审计与问题排查。</p>
 */
@Data
@TableName("process_action_execution")
public class FlowActionExecution {

    /** 执行记录主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联的 flow_action 动作配置 ID */
    private String actionId;
    /** 动作名称（发布快照） */
    private String actionName;
    /** 处理器 Bean 名称（发布快照） */
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
    /** 作用域类型：PROCESS、NODE、SEQUENCE_FLOW */
    private String scopeType;
    /** 绑定的 BPMN 元素 ID */
    private String elementId;
    /** 触发时机编码 */
    private String triggerTiming;
    /** 幂等键，防止同一动作重复执行 */
    private String idempotencyKey;
    /** 触发事件序列化 JSON（执行上下文载荷） */
    private String payloadJson;
    /** 解析后的业务参数 JSON（已脱敏） */
    private String resolvedParamsJson;
    /** 处理器执行结果 JSON（已脱敏） */
    private String resultJson;
    /** 执行轨迹 JSON（含时间、阶段、消息、详情） */
    private String executionTraceJson;
    /** 执行状态，对应 {@link Status} */
    private String status;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetries;
    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;
    /** 错误信息（截断） */
    private String errorMessage;
    /** 错误堆栈（截断） */
    private String errorStack;
    /** 开始执行时间 */
    private LocalDateTime startedAt;
    /** 完成时间 */
    private LocalDateTime finishedAt;
    /** 执行耗时（毫秒） */
    private Long durationMs;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 执行状态枚举。
     */
    public enum Status {
        /** 待执行：已入队等待发件箱工作线程抢占 */
        PENDING,
        /** 执行中：已被抢占并开始调用处理器 */
        RUNNING,
        /** 执行成功 */
        SUCCESS,
        /** 执行失败待重试 */
        FAILED,
        /** 死信：重试耗尽或不可重试，需人工介入 */
        DEAD
    }
}
