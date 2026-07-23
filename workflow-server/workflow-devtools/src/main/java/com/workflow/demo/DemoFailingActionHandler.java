package com.workflow.demo;

import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.FlowActionHandler;
import org.springframework.stereotype.Component;

/**
 * Demo：故意失败的流程动作处理器。
 * 用于验证平台在事务提交后（AFTER_COMMIT）执行动作的失败处理与重试机制。
 */
@Component("demoFailingActionHandler")
public class DemoFailingActionHandler implements FlowActionHandler {

    /**
     * 推荐在事务提交后执行，避免失败回滚主事务。
     *
     * @return 执行模式标识
     */
    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    /**
     * 执行流程动作：直接抛出异常以模拟失败场景。
     *
     * @param ctx 流程动作上下文
     */
    @Override
    public void execute(FlowActionContext ctx) {
        Object message = ctx.getCustomParams().get("message");
        throw new RuntimeException(message == null ? "Demo 流程动作故意失败" : String.valueOf(message));
    }
}
