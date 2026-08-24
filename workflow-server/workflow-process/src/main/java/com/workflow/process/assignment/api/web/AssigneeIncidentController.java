package com.workflow.process.assignment.api.web;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.application.AssigneeIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 空办理人事件管理与处置 API。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/assignee-incidents")
@RequiredArgsConstructor
public class AssigneeIncidentController {

    private final AssigneeIncidentService incidentService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status) {
        require("process:assignee-incident:list");
        return ApiResponse.success(incidentService.list(status));
    }

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        require("process:assignee-incident:list");
        return ApiResponse.success(incidentService.metrics());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        require("process:assignee-incident:list");
        return ApiResponse.success(incidentService.detail(id));
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<Map<String, Object>> handle(
            @PathVariable String id,
            @Valid @RequestBody AssigneeIncidentHandleRequest request) {
        require("process:assignee-incident:handle");
        return ApiResponse.success(incidentService.handle(id, request));
    }

    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限处置空办理人事件：" + permission);
        }
    }
}
