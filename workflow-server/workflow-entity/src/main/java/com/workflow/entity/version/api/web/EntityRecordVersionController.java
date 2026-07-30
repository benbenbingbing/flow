package com.workflow.entity.version.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.version.application.EntityRecordVersionService;
import com.workflow.entity.version.application.model.EntityRecordVersionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务实体数据版本查询与比较接口。
 */
@RestController
@RequestMapping("/api/entity-versions/records")
@RequiredArgsConstructor
@AuthenticatedApi(objectAuthorization = true)
public class EntityRecordVersionController {

    private final EntityRecordVersionService service;
    private final EntityActionCapabilityService actionCapabilityService;

    @GetMapping("/{entityCode}/{recordId}")
    public ApiResponse<List<EntityRecordVersionSummary>> list(
            @PathVariable String entityCode,
            @PathVariable String recordId) {
        requireView(entityCode);
        return ApiResponse.success(
                service.list(entityCode, recordId));
    }

    @GetMapping("/{entityCode}/{recordId}/{versionNo}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @PathVariable Integer versionNo) {
        requireView(entityCode);
        return ApiResponse.success(service.detail(
                entityCode, recordId, versionNo));
    }

    @GetMapping("/{entityCode}/{recordId}/compare")
    public ApiResponse<Map<String, Object>> compare(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @RequestParam("from") Integer fromVersion,
            @RequestParam("to") Integer toVersion) {
        requireView(entityCode);
        return ApiResponse.success(service.compare(
                entityCode,
                recordId,
                fromVersion,
                toVersion));
    }

    private void requireView(String entityCode) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
    }
}
