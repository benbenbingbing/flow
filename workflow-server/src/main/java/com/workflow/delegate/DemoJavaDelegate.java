package com.workflow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 服务任务 Java 类示例
 * 演示如何读取流程变量、设置结果变量，不修改任何业务数据
 */
@Slf4j
@Component
public class DemoJavaDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        log.info("[DemoJavaDelegate] 执行服务任务，流程实例: {}", execution.getProcessInstanceId());

        // 读取流程变量（若不存在则使用默认值）
        Object inputVar = execution.getVariable("inputVar");
        if (inputVar == null) {
            inputVar = "default_value";
        }

        // 仅做计算/转换，不修改数据库
        String result = "processed_" + inputVar;

        // 设置结果变量供后续节点使用
        execution.setVariable("result", result);
        execution.setVariable("demoExecuted", true);

        log.info("[DemoJavaDelegate] 执行完成，inputVar={}, result={}", inputVar, result);
    }
}
