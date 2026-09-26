package com.workflow.biz.project.custom;

import com.workflow.contracts.process.action.context.FlowActionFailureContext;
import com.workflow.contracts.process.action.model.*;
import com.workflow.contracts.process.action.spi.FlowActionFailureStrategyProvider;
import org.springframework.stereotype.Component;
import java.util.*;

/** 可选的异常分类示例；仅允许绑定明确声明可幂等重放的提交后动作。 */
@Component
public class ProjectTransientFailureStrategy implements FlowActionFailureStrategyProvider {
    @Override
    public FailureStrategyDescriptor descriptor() {
        return new FailureStrategyDescriptor("PROJECT_TRANSIENT_RETRY", "1", "网络异常重试后转人工",
                "连接失败或超时使用指数退避，其余异常或次数耗尽时转人工。",
                Set.of(FlowActionExecutionMode.AFTER_COMMIT), Set.of(FailureDisposition.RETRY, FailureDisposition.MANUAL),
                Set.of(), List.of(new FailureStrategyParameter("initialDelaySeconds", "首次等待秒数", "number", true,
                        60, 1L, 21600L, List.of(), "每次重试等待前一次的三倍，最多等待六小时。")));
    }

    /** 异常类型来自只读原因链；预算由平台提供并再次校验，策略无法绕过它。 */
    @Override
    public FailureDecision decide(FlowActionFailureContext context, Map<String, Object> configuration) {
        if (context.exhausted()) return FailureDecision.of(FailureDisposition.MANUAL, "RETRY_EXHAUSTED", "重试次数已耗尽，请人工核查");
        Set<String> temporary = Set.of("java.net.SocketTimeoutException", "java.net.ConnectException",
                "java.net.http.HttpTimeoutException", "java.net.http.HttpConnectTimeoutException");
        if (context.exceptionTypes().stream().anyMatch(temporary::contains)) {
            double delay = ((Number) configuration.get("initialDelaySeconds")).longValue()
                    * Math.pow(3, Math.max(0, context.attemptNo() - 1));
            return FailureDecision.retry((long) Math.min(21600, delay), "网络异常，稍后使用原幂等键重试");
        }
        return FailureDecision.of(FailureDisposition.MANUAL, "MANUAL_REQUIRED", "该异常不适合自动重试，请人工处理");
    }
}
