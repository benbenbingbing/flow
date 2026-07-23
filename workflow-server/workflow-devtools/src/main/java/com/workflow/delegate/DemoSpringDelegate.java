package com.workflow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 服务任务 Spring Bean 示例
 * Bean 名称为 demoServiceTask，可在 BPMN 中通过 ${demoServiceTask} 引用
 * 演示如何读取流程变量、设置结果变量，不修改任何业务数据
 */
@Slf4j
@Component("demoServiceTask")
public class DemoSpringDelegate implements JavaDelegate {

    /**
     * 执行服务任务：读取 amount 变量并按固定折扣计算总额，结果写回流程变量。
     *
     * @param execution 流程执行上下文
     */
    @Override
    public void execute(DelegateExecution execution) {
        log.info("[DemoSpringDelegate] 执行服务任务，流程实例: {}", execution.getProcessInstanceId());

        // 读取流程变量
        Object amount = execution.getVariable("amount");
        if (amount == null) {
            amount = 0;
        }

        // 仅做计算，不修改数据库
        double discount = 0.1;
        double total = ((Number) amount).doubleValue() * (1 - discount);

        // 设置结果变量
        execution.setVariable("discountRate", discount);
        execution.setVariable("totalAmount", total);

        log.info("[DemoSpringDelegate] 执行完成，amount={}, discountRate={}, totalAmount={}", amount, discount, total);
    }
}
