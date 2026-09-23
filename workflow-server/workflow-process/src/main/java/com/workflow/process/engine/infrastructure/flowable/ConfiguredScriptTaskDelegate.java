package com.workflow.process.engine.infrastructure.flowable;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Compatibility delegate for historical deployments.
 */
@Component("configuredScriptTaskDelegate")
public class ConfiguredScriptTaskDelegate implements JavaDelegate {

    /**
     * 执行已配置{@code script}任务委托，并将结果传给后续步骤。
     *
     * @param execution 执行，供本方法执行已配置{@code script}任务委托时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(DelegateExecution execution) {
        throw new IllegalStateException(
                "SCRIPT_TASK_DISABLED: 生产环境禁止执行脚本任务");
    }
}
