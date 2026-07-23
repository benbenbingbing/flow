package com.workflow.demo;

import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.TypedFlowActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Demo：带类型化参数的流程动作处理器。
 *
 * <p>平台会自动将 paramsJson 映射为 {@link DemoActionParams}。</p>
 */
@Slf4j
@Component("demoTypedActionHandler")
public class DemoTypedActionHandler implements TypedFlowActionHandler<DemoActionParams> {

    /**
     * 返回类型化参数的类型，供平台将 paramsJson 反序列化为该类型。
     *
     * @return 参数类型
     */
    @Override
    public Class<DemoActionParams> getParamType() {
        return DemoActionParams.class;
    }

    /**
     * 执行流程动作：打印动作上下文与类型化参数的详细信息，用于联调演示。
     *
     * @param ctx    流程动作上下文
     * @param params 类型化业务参数
     */
    @Override
    public void execute(FlowActionContext ctx, DemoActionParams params) {
        log.info("========== [DemoTypedActionHandler] 流程动作执行开始 ==========");
        log.info("动作ID: {}", ctx.getActionId());
        log.info("动作名称: {}", ctx.getActionName());
        log.info("流程实例ID: {}", ctx.getProcessInstanceId());
        log.info("实体编码: {}", ctx.getEntityCode());
        log.info("实体数据ID: {}", ctx.getEntityDataId());
        log.info("顺序流ID: {}", ctx.getSequenceFlowId());
        log.info("源节点ID: {}, 源节点名称: {}", ctx.getSourceNodeId(), ctx.getSourceNodeName());
        log.info("目标节点ID: {}, 目标节点名称: {}", ctx.getTargetNodeId(), ctx.getTargetNodeName());
        log.info("类型化参数: message={}, notifyUser={}, priority={}",
                params.getMessage(), params.getNotifyUser(), params.getPriority());
        log.info("自定义参数(原始): {}", ctx.getCustomParams());
        log.info("流程变量: {}", ctx.getVariables());
        log.info("实体数据: {}", ctx.getEntityData());
        log.info("当前任务: {}", ctx.getCurrentTask());
        log.info("========== [DemoTypedActionHandler] 流程动作执行结束 ==========");
    }
}
