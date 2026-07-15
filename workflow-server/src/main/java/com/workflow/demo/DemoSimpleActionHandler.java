package com.workflow.demo;

import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.FlowActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Demo：不带类型化参数的流程动作处理器。
 *
 * <p>通过 {@link FlowActionContext#getCustomParams()} 直接读取前端传入的业务参数。</p>
 */
@Slf4j
@Component("demoSimpleActionHandler")
public class DemoSimpleActionHandler implements FlowActionHandler {

    @Override
    public void execute(FlowActionContext ctx) {
        log.info("========== [DemoSimpleActionHandler] 流程动作执行开始 ==========");
        log.info("动作ID: {}", ctx.getActionId());
        log.info("动作名称: {}", ctx.getActionName());
        log.info("流程实例ID: {}", ctx.getProcessInstanceId());
        log.info("实体编码: {}", ctx.getEntityCode());
        log.info("实体数据ID: {}", ctx.getEntityDataId());
        log.info("顺序流ID: {}", ctx.getSequenceFlowId());
        log.info("源节点ID: {}, 源节点名称: {}", ctx.getSourceNodeId(), ctx.getSourceNodeName());
        log.info("目标节点ID: {}, 目标节点名称: {}", ctx.getTargetNodeId(), ctx.getTargetNodeName());
        log.info("自定义参数: {}", ctx.getCustomParams());
        log.info("流程变量: {}", ctx.getVariables());
        log.info("实体数据: {}", ctx.getEntityData());
        log.info("当前任务: {}", ctx.getCurrentTask());
        log.info("========== [DemoSimpleActionHandler] 流程动作执行结束 ==========");
    }
}
