package com.workflow.contracts.process.action.context;

import java.util.List;

/**
 * 失败发生后提供给策略的只读事实，不暴露实体、数据库连接或修改流程的助手。
 * attemptNo 首次为 1；maxRetries 表示首次之外的额外重试次数。
 * exceptionTypes 包含外层异常及 cause 类型，策略可以自行分类，也可以不做任何条件判断。
 */
public record FlowActionFailureContext(String actionExecutionId, String actionId,
        String processInstanceId, String processVersionId, String engineExecutionId,
        String entityCode, String triggerTiming, String executionMode,
        int attemptNo, int maxRetries, boolean retryable, List<String> exceptionTypes,
        String errorMessage) {
    public FlowActionFailureContext {
        exceptionTypes = List.copyOf(exceptionTypes);
    }

    /** 是否已耗尽本轮自动执行预算，供策略决定忽略或转人工。 */
    public boolean exhausted() { return attemptNo >= 1 + maxRetries; }
}
