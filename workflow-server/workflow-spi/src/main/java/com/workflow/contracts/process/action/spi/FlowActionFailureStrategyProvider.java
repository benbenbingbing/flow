package com.workflow.contracts.process.action.spi;

import com.workflow.contracts.process.action.context.FlowActionFailureContext;
import com.workflow.contracts.process.action.model.FailureDecision;
import com.workflow.contracts.process.action.model.FailureStrategyDescriptor;
import java.util.Map;

/** 项目实现并注册 Spring Bean 后，配置人员可以在流程动作中选择该失败策略。 */
public interface FlowActionFailureStrategyProvider {
    /** 返回稳定身份、适用范围和参数定义；发布版本会固定编码与版本。 */
    FailureStrategyDescriptor descriptor();

    /**
     * 在动作抛异常后判断处理方式；不需要新增失败触发条件。
     * 参数已按 Schema 校验且不可修改。实现应为快速纯计算，不写业务数据或调用外部系统。
     * 返回非法决定或抛异常时平台保留原错误，并按执行方式保守兜底。
     */
    FailureDecision decide(FlowActionFailureContext context, Map<String, Object> configuration);
}
