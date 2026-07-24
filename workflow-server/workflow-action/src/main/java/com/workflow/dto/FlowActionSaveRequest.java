package com.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程动作保存请求 DTO。
 *
 * <p>由前端提交，用于新增或更新草稿状态的流程动作配置。</p>
 */
@Data
public class FlowActionSaveRequest {

    /** 动作 ID；新增时为空 */
    private String id;

    /** 流程定义配置 ID */
    @NotBlank(message = "流程配置 ID 不能为空")
    private String processConfigId;

    /** 顺序流 ID（兼容旧字段，新配置使用 elementId） */
    private String sequenceFlowId;

    /** 作用域类型：PROCESS、NODE、SEQUENCE_FLOW */
    private String scopeType;

    /** 绑定的 BPMN 元素 ID；流程级可空 */
    private String elementId;

    /** 触发时机编码 */
    private String triggerTiming;

    /** 执行方式：IN_TRANSACTION、AFTER_COMMIT */
    private String executionMode;

    /** 失败策略：ROLLBACK、CONTINUE、RETRY、IGNORE */
    private String failurePolicy;

    /** 动作名称 */
    @NotBlank(message = "动作名称不能为空")
    private String actionName;

    /** 动作描述 */
    private String description;

    /** 处理器 Bean 名称 */
    @NotBlank(message = "处理器不能为空")
    private String interfaceName;

    /** 方法名（默认 execute） */
    private String methodName;
    /** 参数 JSON */
    private String paramsJson;
    /** 执行顺序（越小越先执行） */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;
    /** 重试配置 JSON */
    private String retryConfig;

    /** 动作定义目录 ID */
    private String actionDefinitionId;
}
