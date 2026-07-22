package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.TaskAddSignRequest;
import com.workflow.dto.TaskCcRequest;
import com.workflow.service.ProcessCcRuntimeService;
import com.workflow.service.TaskAddSignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TaskOperationController {
    private final TaskAddSignService taskAddSignService;
    private final ProcessCcRuntimeService ccRuntimeService;

    @GetMapping("/api/tasks/{taskId}/operations")
    public Result<Map<String, Object>> operations(@PathVariable String taskId) {
        Map<String, Object> operations = new LinkedHashMap<>(taskAddSignService.operations(taskId));
        operations.put("manualCc", ccRuntimeService.isManualCcAllowed(taskId));
        return Result.success(operations);
    }

    @GetMapping("/api/tasks/{taskId}/add-sign-preview")
    public Result<Map<String, Object>> preview(
            @PathVariable String taskId,
            @RequestParam List<String> userIds,
            @RequestParam(defaultValue = "PARALLEL") String type) {
        return Result.success(taskAddSignService.preview(taskId, userIds, type));
    }

    @PostMapping("/api/tasks/{taskId}/add-sign")
    public Result<Map<String, Object>> addSign(
            @PathVariable String taskId,
            @Valid @RequestBody TaskAddSignRequest request) {
        return Result.success(taskAddSignService.addSign(taskId, request));
    }

    @PostMapping("/api/tasks/{taskId}/cc")
    public Result<Map<String, Object>> manualCc(
            @PathVariable String taskId,
            @Valid @RequestBody TaskCcRequest request) {
        return Result.success(Map.of("created", ccRuntimeService.manualCc(taskId, request)));
    }

    @PostMapping("/api/add-sign/{addSignId}/cancel")
    public Result<Void> cancel(@PathVariable String addSignId) {
        taskAddSignService.cancel(addSignId);
        return Result.success();
    }
}
