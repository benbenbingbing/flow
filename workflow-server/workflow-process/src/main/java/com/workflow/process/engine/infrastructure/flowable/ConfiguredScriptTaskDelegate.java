package com.workflow.process.engine.infrastructure.flowable;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Compatibility delegate for historical deployments.
 */
@Component("configuredScriptTaskDelegate")
public class ConfiguredScriptTaskDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        throw new IllegalStateException(
                "SCRIPT_TASK_DISABLED: 生产环境禁止执行脚本任务");
    }
}
