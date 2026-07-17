package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.FlowActionExecutionDetailDTO;
import com.workflow.service.CurrentUserRoleService;
import com.workflow.service.FlowActionExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/process-action-executions")
@RequiredArgsConstructor
public class FlowActionExecutionController {

    private final FlowActionExecutionService executionService;
    private final CurrentUserRoleService currentUserRoleService;

    @GetMapping("/process/{processInstanceId}")
    public ApiResponse<List<FlowActionExecutionDetailDTO>> listByProcessInstance(
            @PathVariable String processInstanceId) {
        currentUserRoleService.requireSuperAdmin();
        return ApiResponse.success(executionService.findDetailsByProcessInstanceId(processInstanceId));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable String id) {
        currentUserRoleService.requireSuperAdmin();
        executionService.retry(id);
        return ApiResponse.success();
    }
}
