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

@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequiredArgsConstructor
public class TaskSlaController {

    private final TaskSlaRuntimeService slaService;
    private final TaskActionService taskActionService;

    @GetMapping("/api/tasks/{taskId}/sla")
    public Result<TaskSlaDTO> detail(@PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(slaService.detail(taskId));
    }

    @PostMapping("/api/tasks/{taskId}/acknowledge")
    public Result<ProcessTaskSla> acknowledge(
            @PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(slaService.acknowledge(
                taskId,
                com.workflow.admin.security.context.UserContext
                        .requireUsernameOrId()));
    }

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

    @PostMapping("/api/tasks/{taskId}/sla/resume")
    public Result<ProcessTaskSla> resume(
            @PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(slaService.resume(taskId));
    }
}
