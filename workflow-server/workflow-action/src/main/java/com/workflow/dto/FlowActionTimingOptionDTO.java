package com.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 流程动作触发时机选项 DTO。
 *
 * <p>供前端在动作配置时选择触发时机，包含编码、展示名、说明、适用作用域、
 * 是否仅限用户任务、默认执行方式、默认失败策略与可用上下文等信息。</p>
 */
@Data
@AllArgsConstructor
public class FlowActionTimingOptionDTO {
    /** 时机编码，对应枚举名或自定义编码 */
    private String value;
    /** 展示名 */
    private String label;
    /** 用途说明 */
    private String description;
    /** 适用作用域 */
    private String scopeType;
    /** 是否仅限用户任务节点 */
    private Boolean userTaskOnly;
    /** 默认执行方式 */
    private String defaultExecutionMode;
    /** 默认失败策略 */
    private String defaultFailurePolicy;
    /** 可用上下文描述 */
    private String availableContext;
    /** 是否为扩展自定义时机 */
    private Boolean custom;
}
