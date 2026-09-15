package com.workflow.biz.project.zdw;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("ZdwCustomFlowAction")
@Slf4j
public class ZdwCustomFlowAction implements FlowActionHandler {
    @Override
    public void execute(FlowActionContext ctx) {

        log.info("ZdwCustomFlowAction execute, processInstanceId: {}, taskId: {}, actionName: {},ctx : {}",
                ctx.getProcessInstanceId(), ctx.getTaskId(), ctx.getActionName(),ctx);
    }
}
