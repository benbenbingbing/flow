package com.workflow.process.sla.runtime.api.web;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.sla.runtime.application.TaskSlaMonitorService;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/task-sla/monitor")
@RequiresPermission("process:sla:monitor")
@RequiredArgsConstructor
public class TaskSlaMonitorController {

    private final TaskSlaMonitorService service;

    @GetMapping
    public Result<PageResult<ProcessTaskSla>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String keyword) {
        return Result.success(service.page(
                pageNum,
                pageSize,
                status,
                processKey,
                assignee,
                keyword));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Long>> statistics() {
        return Result.success(service.statistics());
    }
}
