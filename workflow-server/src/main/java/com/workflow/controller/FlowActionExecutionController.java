package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.entity.FlowActionExecution;
import com.workflow.service.FlowActionExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flow-action-executions")
@RequiredArgsConstructor
public class FlowActionExecutionController {

    private final FlowActionExecutionService executionService;

    @GetMapping("/process/{processInstanceId}")
    public ApiResponse<List<FlowActionExecution>> listByProcessInstance(
            @PathVariable String processInstanceId) {
        return ApiResponse.success(executionService.findByProcessInstanceId(processInstanceId));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable String id) {
        executionService.retry(id);
        return ApiResponse.success();
    }
}
