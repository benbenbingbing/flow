package com.workflow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;

/**
 * 服务任务表达式示例服务
 * 可在 BPMN 表达式中调用，如：${demoExpressionService.execute(execution)}
 * 演示如何读取流程变量、设置结果变量，不修改任何业务数据
 */
@Slf4j
@Service("demoExpressionService")
public class DemoExpressionService {

    /**
     * 执行表达式服务：根据 status 变量推导下一状态并写回流程变量。
     *
     * @param execution 流程执行上下文
     */
    public void execute(DelegateExecution execution) {
        log.info("[DemoExpressionService] 执行表达式服务，流程实例: {}", execution.getProcessInstanceId());

        // 读取流程变量
        String status = (String) execution.getVariable("status");
        if (status == null) {
            status = "pending";
        }

        // 仅做逻辑判断，不修改数据库
        String nextStatus = "approved".equals(status) ? "completed" : "rejected";

        // 设置结果变量
        execution.setVariable("nextStatus", nextStatus);
        execution.setVariable("processedTime", System.currentTimeMillis());

        log.info("[DemoExpressionService] 执行完成，status={}, nextStatus={}", status, nextStatus);
    }

    /**
     * 根据流程变量 score 计算等级（A/B/C/D），并写回 scoreLevel 变量。
     *
     * @param execution 流程执行上下文
     * @return 计算得到的等级字符串
     */
    public String evaluate(DelegateExecution execution) {
        Object score = execution.getVariable("score");
        if (score == null) {
            score = 60;
        }
        int s = ((Number) score).intValue();
        String level = s >= 90 ? "A" : s >= 80 ? "B" : s >= 60 ? "C" : "D";
        execution.setVariable("scoreLevel", level);
        return level;
    }
}
