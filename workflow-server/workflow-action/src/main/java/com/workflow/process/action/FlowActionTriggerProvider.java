package com.workflow.process.action;

import com.workflow.dto.FlowActionTimingOptionDTO;

import java.util.Collection;

/**
 * 流程动作触发时机扩展提供器。
 *
 * <p>业务模块可通过实现该接口向动作目录注入自定义触发时机选项，
 * 与平台内置的 {@link FlowActionTriggerTiming} 合并供前端选择。</p>
 */
public interface FlowActionTriggerProvider {

    /**
     * 返回该提供器支持的自定义触发时机选项集合。
     *
     * @return 自定义触发时机选项集合；无自定义项时返回空集合
     */
    Collection<FlowActionTimingOptionDTO> getTriggerOptions();
}
