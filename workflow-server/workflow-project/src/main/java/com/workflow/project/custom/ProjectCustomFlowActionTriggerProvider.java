package com.workflow.project.custom;

import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.FlowActionFailurePolicy;
import com.workflow.contracts.action.FlowActionScopeType;
import com.workflow.contracts.action.FlowActionTimingOption;
import com.workflow.contracts.action.FlowActionTriggerProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 自定义流程动作触发时机示例。
 *
 * <p>该实现向流程动作设计器增加
 * {@code PROJECT_CUSTOM_MANUAL_EVENT}。业务代码需要调用
 * {@code FlowActionDispatcher.dispatchCustom(...)} 才会真正触发。</p>
 */
@Slf4j
@Component
public class ProjectCustomFlowActionTriggerProvider
        implements FlowActionTriggerProvider {

    public static final String TIMING =
            "PROJECT_CUSTOM_MANUAL_EVENT";

    @Override
    public Collection<FlowActionTimingOption>
            getTriggerOptions() {
        List<FlowActionTimingOption> options =
                List.of(new FlowActionTimingOption(
                TIMING,
                "项目自定义业务事件",
                "由项目业务代码显式分发，用于验证自定义触发时机。",
                FlowActionScopeType.PROCESS.name(),
                false,
                FlowActionExecutionMode.AFTER_COMMIT.name(),
                FlowActionFailurePolicy.IGNORE.name(),
                "流程实例、实体记录、变量快照和自定义参数",
                true));
        log.info(
                "项目自定义流程动作触发时机目录读取: timing={}, scopeType={}, executionMode={}, failurePolicy={}, optionCount={}",
                TIMING,
                FlowActionScopeType.PROCESS.name(),
                FlowActionExecutionMode.AFTER_COMMIT.name(),
                FlowActionFailurePolicy.IGNORE.name(),
                options.size());
        return options;
    }
}
