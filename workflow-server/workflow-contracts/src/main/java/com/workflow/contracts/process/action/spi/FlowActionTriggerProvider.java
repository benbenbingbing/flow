package com.workflow.contracts.process.action.spi;

import com.workflow.contracts.action.FlowActionTimingOption;
import java.util.Collection;

/**
 * 向流程动作目录注入自定义触发时机选项的扩展点。
 */
public interface FlowActionTriggerProvider {

    /**
     * 返回该提供器支持的自定义触发时机选项集合。
     *
     * @return 自定义触发时机选项集合；无自定义项时返回空集合
     */
    Collection<FlowActionTimingOption> getTriggerOptions();
}
