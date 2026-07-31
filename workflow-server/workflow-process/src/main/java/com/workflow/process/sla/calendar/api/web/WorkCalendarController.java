package com.workflow.process.sla.calendar.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.sla.calendar.api.request.WorkCalendarSaveRequest;
import com.workflow.process.sla.calendar.api.request.WorkCalendarSimulationRequest;
import com.workflow.process.sla.calendar.api.response.WorkCalendarDTO;
import com.workflow.process.sla.calendar.application.WorkCalendarService;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-calendars")
@RequiredArgsConstructor
@RequiresPermission("system:work-calendar:view")
public class WorkCalendarController {

    private final WorkCalendarService service;

    @GetMapping
    public Result<List<WorkCalendar>> list() {
        return Result.success(service.list());
    }

    @GetMapping("/{id}")
    public Result<WorkCalendarDTO> get(@PathVariable String id) {
        return Result.success(service.get(id));
    }

    @PostMapping
    @RequiresPermission("system:work-calendar:manage")
    public Result<WorkCalendarDTO> create(
            @RequestBody WorkCalendarSaveRequest request) {
        return Result.success(service.save(null, request));
    }

    @PostMapping("/{id}/update")
    @RequiresPermission("system:work-calendar:manage")
    public Result<WorkCalendarDTO> update(
            @PathVariable String id,
            @RequestBody WorkCalendarSaveRequest request) {
        return Result.success(service.save(id, request));
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("system:work-calendar:publish")
    public Result<WorkCalendarDTO> publish(@PathVariable String id) {
        return Result.success(service.publish(id));
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("system:work-calendar:manage")
    public Result<Void> disable(@PathVariable String id) {
        service.disable(id);
        return Result.success();
    }

    @PostMapping("/{id}/simulate")
    public Result<Map<String, Object>> simulate(
            @PathVariable String id,
            @RequestBody WorkCalendarSimulationRequest request) {
        if (request == null
                || request.startAt() == null
                || request.minutes() == null
                || request.minutes() < 0) {
            throw new IllegalArgumentException(
                    "模拟开始时间和分钟数不能为空");
        }
        OffsetDateTime dueAt = service.simulate(
                        id,
                        request.startAt().toInstant(),
                        request.minutes())
                .atOffset(ZoneOffset.UTC);
        return Result.success(Map.of("dueAt", dueAt));
    }
}
