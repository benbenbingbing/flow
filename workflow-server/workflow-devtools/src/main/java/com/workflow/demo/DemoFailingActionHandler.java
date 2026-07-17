package com.workflow.demo;

import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.FlowActionHandler;
import org.springframework.stereotype.Component;

@Component("demoFailingActionHandler")
public class DemoFailingActionHandler implements FlowActionHandler {

    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    @Override
    public void execute(FlowActionContext ctx) {
        Object message = ctx.getCustomParams().get("message");
        throw new RuntimeException(message == null ? "Demo 流程动作故意失败" : String.valueOf(message));
    }
}
