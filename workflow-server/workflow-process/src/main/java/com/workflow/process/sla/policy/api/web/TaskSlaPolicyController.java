package com.workflow.process.sla.policy.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.sla.policy.api.request.TaskSlaPolicySaveRequest;
import com.workflow.process.sla.policy.api.response.TaskSlaPolicyDTO;
import com.workflow.process.sla.policy.application.TaskSlaPolicyService;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/task-sla-policies")
@RequiredArgsConstructor
@RequiresPermission("process:sla-policy:view")
public class TaskSlaPolicyController {

    private final TaskSlaPolicyService service;

    @GetMapping
    public Result<List<TaskSlaPolicy>> list() {
        return Result.success(service.list());
    }

    @GetMapping("/published")
    public Result<List<TaskSlaPolicy>> published() {
        return Result.success(service.published());
    }

    @GetMapping("/{id}")
    public Result<TaskSlaPolicyDTO> get(@PathVariable String id) {
        return Result.success(service.get(id));
    }

    @PostMapping
    @RequiresPermission("process:sla-policy:manage")
    public Result<TaskSlaPolicyDTO> create(
            @RequestBody TaskSlaPolicySaveRequest request) {
        return Result.success(service.save(null, request));
    }

    @PostMapping("/{id}/update")
    @RequiresPermission("process:sla-policy:manage")
    public Result<TaskSlaPolicyDTO> update(
            @PathVariable String id,
            @RequestBody TaskSlaPolicySaveRequest request) {
        return Result.success(service.save(id, request));
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("process:sla-policy:publish")
    public Result<TaskSlaPolicyDTO> publish(@PathVariable String id) {
        return Result.success(service.publish(id));
    }
}
