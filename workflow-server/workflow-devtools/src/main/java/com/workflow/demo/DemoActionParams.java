package com.workflow.demo;

import lombok.Data;

/**
 * Demo 流程动作类型化业务参数。
 *
 * <p>配置示例 paramsJson：</p>
 * <pre>
 * {
 *   "message": "审批通过",
 *   "notifyUser": true,
 *   "priority": 1
 * }
 * </pre>
 */
@Data
public class DemoActionParams {

    /**
     * 动作消息
     */
    private String message;

    /**
     * 是否通知用户
     */
    private Boolean notifyUser;

    /**
     * 优先级
     */
    private Integer priority;
}
