package com.workflow.biz.project.zdw;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 封装{@code zdw}自定义流程动作相关能力和状态；供同一业务流程的后续处理使用。
 */
@Component("ZdwCustomFlowAction")
@Slf4j
public class ZdwCustomFlowAction implements FlowActionHandler {
    /**
     * 执行{@code zdw}自定义流程动作，并将结果传给后续步骤。
     *
     * @param ctx {@code ctx}，供本方法执行{@code zdw}自定义流程动作时使用
     */
    @Override
    public void execute(FlowActionContext ctx) {

        log.info("ZdwCustomFlowAction execute, processInstanceId: {}, taskId: {}, actionName: {},ctx : {}",
                ctx.getProcessInstanceId(), ctx.getTaskId(), ctx.getActionName(),ctx);
    }
}
