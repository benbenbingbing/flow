package com.workflow.migration.collaboration.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.BranchCreateRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.BranchSaveRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.CommentRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.MergeRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.ReviewDecisionRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.ReviewRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.ScheduleRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationModels.WorkspaceSaveRequest;
import com.workflow.migration.collaboration.application.ConfigurationCollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 配置工作区、分支、评论、评审和定时发布接口。 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/config-collaboration")
@RequiredArgsConstructor
public class ConfigurationCollaborationController {

    private final ConfigurationCollaborationService service;

    @GetMapping("/workspaces")
    @RequiresPermission("platform:capability:list")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/workspaces/{id}")
    @RequiresPermission("platform:capability:list")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping("/workspaces")
    @RequiresPermission("config-collaboration:manage")
    public ApiResponse<Map<String, Object>> save(@RequestBody WorkspaceSaveRequest request) {
        return ApiResponse.success(service.save(request));
    }

    @PostMapping("/workspaces/{id}/branches")
    @RequiresPermission("config-collaboration:manage")
    public ApiResponse<Map<String, Object>> branch(
            @PathVariable String id,
            @RequestBody BranchCreateRequest request) {
        return ApiResponse.success(service.createBranch(id, request));
    }

    @PostMapping("/branches/{id}")
    @RequiresPermission("config-collaboration:manage")
    public ApiResponse<Map<String, Object>> saveBranch(
            @PathVariable String id,
            @RequestBody BranchSaveRequest request) {
        return ApiResponse.success(service.saveBranch(id, request));
    }

    @PostMapping("/branches/{id}/merge")
    @RequiresPermission("config-collaboration:manage")
    public ApiResponse<Map<String, Object>> merge(
            @PathVariable String id,
            @RequestBody MergeRequest request) {
        return ApiResponse.success(service.merge(id, request));
    }

    @PostMapping("/workspaces/{id}/comments")
    @RequiresPermission("config-collaboration:manage")
    public ApiResponse<Map<String, Object>> comment(
            @PathVariable String id,
            @RequestBody CommentRequest request) {
        return ApiResponse.success(service.addComment(id, request));
    }

    @PostMapping("/workspaces/{id}/reviews")
    @RequiresPermission("config-collaboration:manage")
    public ApiResponse<Map<String, Object>> review(
            @PathVariable String id,
            @RequestBody(required = false) ReviewRequest request) {
        return ApiResponse.success(service.requestReview(id, request));
    }

    @PostMapping("/reviews/{id}/decision")
    @RequiresPermission("config-collaboration:review")
    public ApiResponse<Map<String, Object>> decide(
            @PathVariable String id,
            @RequestBody ReviewDecisionRequest request) {
        return ApiResponse.success(service.decideReview(id, request));
    }

    @PostMapping("/workspaces/{id}/schedules")
    @RequiresPermission("config-collaboration:schedule")
    public ApiResponse<Map<String, Object>> schedule(
            @PathVariable String id,
            @RequestBody ScheduleRequest request) {
        return ApiResponse.success(service.schedule(id, request));
    }

    @PostMapping("/schedules/{id}/execute")
    @RequiresPermission("config-collaboration:schedule")
    public ApiResponse<Map<String, Object>> execute(@PathVariable String id) {
        return ApiResponse.success(service.executeScheduled(id));
    }
}
