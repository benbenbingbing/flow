package com.workflow.process.sla.runtime.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.process.sla.runtime.api.response.TaskSlaDTO;
import com.workflow.process.sla.runtime.application.TaskSlaRuntimeService;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import com.workflow.process.task.application.TaskActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 办理人任务 SLA 入口；每次读取或变更前重新检查当前任务访问权。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequiredArgsConstructor
public class TaskSlaController {

    private final TaskSlaRuntimeService slaService;
    private final TaskActionService taskActionService;

    /**
     * 读取当前任务的 SLA 状态、暂停记录和升级事件，供办理页展示。
     *
     * @param taskId 路由中的 Flowable 任务 ID，先用于当前用户访问校验
     * @return 任务 SLA 详情
     */
    @GetMapping("/api/tasks/{taskId}/sla")
    public Result<TaskSlaDTO> detail(@PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(slaService.detail(taskId));
    }

    /**
     * 办理人首次响应任务时结算响应指标，重复请求由服务层幂等处理。
     *
     * @param taskId 需要结算响应指标的任务 ID
     * @return 更新后的 SLA 状态
     */
    @PostMapping("/api/tasks/{taskId}/acknowledge")
    public Result<ProcessTaskSla> acknowledge(
            @PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(slaService.acknowledge(
                taskId,
                com.workflow.admin.security.context.UserContext
                        .requireUsernameOrId()));
    }

    /**
     * 暂停当前任务 SLA；reason 用于暂停历史，pauseType 决定人工暂停或流程挂起规则，
     * 最终是否允许暂停仍由发布策略校验。
     *
     * @param taskId 需要暂停计时的任务 ID
     * @param request 可选的暂停原因和类型，写入暂停记录并参与策略判断
     * @return 暂停后的 SLA 状态
     */
    @PostMapping("/api/tasks/{taskId}/sla/pause")
    public Result<ProcessTaskSla> pause(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, String> request) {
        taskActionService.requireTaskAccess(taskId);
        Map<String, String> body =
                request == null ? Map.of() : request;
        return Result.success(slaService.pause(
                taskId,
                body.get("reason"),
                body.get("pauseType")));
    }

    /**
     * 恢复当前任务计时并重算截止事件，供办理人继续处理。
     *
     * @param taskId 需要恢复计时的任务 ID
     * @return 恢复后的 SLA 状态
     */
    @PostMapping("/api/tasks/{taskId}/sla/resume")
    public Result<ProcessTaskSla> resume(
            @PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(slaService.resume(taskId));
    }
}
