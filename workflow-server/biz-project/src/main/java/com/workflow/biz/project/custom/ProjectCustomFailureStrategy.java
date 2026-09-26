package com.workflow.biz.project.custom;

import com.workflow.contracts.process.action.context.FlowActionFailureContext;
import com.workflow.contracts.process.action.model.*;
import com.workflow.contracts.process.action.spi.FlowActionFailureStrategyProvider;
import org.springframework.stereotype.Component;
import java.util.*;

/** 无额外触发条件的自定义策略示例：事务内阻断操作，提交后进入人工处理列表。 */
@Component
public class ProjectCustomFailureStrategy implements FlowActionFailureStrategyProvider {
    @Override
    public FailureStrategyDescriptor descriptor() {
        return new FailureStrategyDescriptor("PROJECT_FAILURE_MANUAL", "1", "项目失败转人工",
                "动作抛出异常后执行：事务内回滚本次操作，提交后停止自动执行并转人工。",
                Set.of(FlowActionExecutionMode.IN_TRANSACTION, FlowActionExecutionMode.AFTER_COMMIT),
                Set.of(FailureDisposition.ROLLBACK, FailureDisposition.MANUAL), Set.of(),
                List.of(new FailureStrategyParameter("message", "处理说明", "string", true,
                        "请核查业务数据后重新处理", null, null, List.of(), "显示在执行记录中，帮助管理员处理失败。")));
    }

    /** 正常执行不进入本方法；不创建审批任务，也不改变已提交的流程状态。 */
    @Override
    public FailureDecision decide(FlowActionFailureContext context, Map<String, Object> configuration) {
        boolean transactional = "IN_TRANSACTION".equals(context.executionMode());
        return FailureDecision.of(transactional ? FailureDisposition.ROLLBACK : FailureDisposition.MANUAL,
                transactional ? "ROLLED_BACK" : "MANUAL_REQUIRED", (String) configuration.get("message"));
    }
}
