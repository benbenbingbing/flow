package com.workflow.process.command.application;

import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/** 公共能力共享的执行身份边界；无身份线程不能因来自后台自动获得权限。 */
@Component
@RequiredArgsConstructor
public class ProcessInvocationAccess {
    private final CurrentActorPort actors;
    private final ProcessInstanceAccessService instances;
    private final TaskIdentityAccessService tasks;

    /** 返回稳定的调用范围，供命令幂等隔离使用；动作范围优先于偶然存在的请求身份。 */
    public String invocationKey() {
        String action = ProcessInvocationScope.key();
        if (action != null) return action;
        CurrentActor actor = requireActor();
        return "user:" + (hasText(actor.userId()) ? actor.userId() : actor.username());
    }

    /** 取得真实人工操作人；动作执行范围本身不授予人工任务办理权。 */
    public CurrentActor requireActor() {
        CurrentActor actor = actors.current();
        if (actor == null || !hasText(actor.userId()) && !hasText(actor.username())) {
            throw new ProcessOperationException("PROCESS_ACTOR_REQUIRED", "流程操作缺少执行身份", null);
        }
        return actor;
    }

    /** 按实例检查读取范围；调用方必须先确认实例存在，避免把技术异常当成未授权。 */
    public void requireRead(String instanceId) {
        if (ProcessInvocationScope.allows(instanceId)) return;
        requireActor();
        try { instances.requireReadAccess(instanceId); }
        catch (ForbiddenException error) { throw denied(instanceId, error); }
    }

    /** 只有真实任务办理人/候选人或当前流程动作可修改该引擎任务的业务变量。 */
    public void requireTaskVariables(Task task) {
        if (ProcessInvocationScope.allows(task.getProcessInstanceId())) return;
        requireActor();
        try { tasks.requireCurrentUserAccess(task); }
        catch (ForbiddenException error) { throw denied(task.getId(), error); }
    }

    /** 流程变量写入授权由命令服务结合活动任务判断；签发动作只覆盖自己的实例。 */
    public boolean isActionFor(String instanceId) { return ProcessInvocationScope.allows(instanceId); }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static ProcessOperationException denied(String id, RuntimeException error) {
        return new ProcessOperationException("PROCESS_ACCESS_DENIED", error.getMessage(), id, error);
    }
}
