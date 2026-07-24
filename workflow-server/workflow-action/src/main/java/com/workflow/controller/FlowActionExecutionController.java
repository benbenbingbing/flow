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

/**
 * 流程动作执行记录接口。
 *
 * <p>面向超级管理员提供流程动作执行详情查询与死信/失败记录的手动重试能力。</p>
 */
@RestController
@RequestMapping("/api/process-action-executions")
@RequiredArgsConstructor
public class FlowActionExecutionController {

    private final FlowActionExecutionService executionService;
    private final CurrentUserRoleService currentUserRoleService;

    /**
     * 查询流程实例下的全部执行详情。
     *
     * @param processInstanceId 流程实例 ID
     * @return 执行详情列表
     */
    @GetMapping("/process/{processInstanceId}")
    public ApiResponse<List<FlowActionExecutionDetailDTO>> listByProcessInstance(
            @PathVariable String processInstanceId) {
        currentUserRoleService.requireSuperAdmin();
        return ApiResponse.success(executionService.findDetailsByProcessInstanceId(processInstanceId));
    }

    /**
     * 手动重试死信或失败状态的执行记录。
     *
     * @param id 执行记录 ID
     * @return 空响应
     */
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable String id) {
        currentUserRoleService.requireSuperAdmin();
        executionService.retry(id);
        return ApiResponse.success();
    }
}
