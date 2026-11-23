package com.workflow.process.action.application;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.core.error.ForbiddenException;

/** 仅由流程执行器建立的同步调用范围，供业务 Port 取得服务端发布身份。 */
public final class PublishedFlowActionInvocation {
    private static final ThreadLocal<Invocation> CURRENT = new ThreadLocal<>();

    private PublishedFlowActionInvocation() { }

    public record Invocation(String actionId, String processVersionId, String processDefinitionId,
            String processInstanceId, String entityCode, String recordId, String operatorId,
            String executionKey, String executionMode, String failurePolicy, String publishedParameters) { }

    public static Invocation requireCurrent() {
        var invocation = CURRENT.get();
        if (invocation == null) throw new ForbiddenException("关联流程协调必须在已发布流程动作内运行");
        return invocation;
    }

    /** 流程动作只继承对其发布执行绑定的那条实体记录的宿主读取权。 */
    public static boolean matchesRecord(String entityCode, String recordId) {
        var invocation = CURRENT.get();
        return invocation != null && entityCode != null && recordId != null
                && entityCode.equals(invocation.entityCode()) && recordId.equals(invocation.recordId());
    }

    static void run(FlowActionContext context, String parameters, Runnable handler) {
        var previous = CURRENT.get();
        // 复制执行器确认的标识，防止处理器修改传入的可变上下文后改变 Port 的授权目标。
        CURRENT.set(new Invocation(context.getActionId(), context.getProcessVersionId(),
                context.getProcessDefinitionId(), context.getProcessInstanceId(), context.getEntityCode(),
                context.getEntityDataId(), context.getOperatorId(), context.getIdempotencyKey(),
                context.getExecutionMode(), context.getFailurePolicy(), parameters));
        try {
            handler.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
