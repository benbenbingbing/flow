package com.workflow.entity.version.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
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
@RequiresPermission("entity:version:config:list")
public class EntityRecordVersionController {

    private final EntityRecordVersionService service;

    @GetMapping("/{entityCode}/{recordId}")
    public ApiResponse<List<EntityRecordVersionSummary>> list(
            @PathVariable String entityCode,
            @PathVariable String recordId) {
        return ApiResponse.success(
                service.list(entityCode, recordId));
    }

    @GetMapping("/{entityCode}/{recordId}/{versionNo}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @PathVariable Integer versionNo) {
        return ApiResponse.success(service.detail(
                entityCode, recordId, versionNo));
    }

    @GetMapping("/{entityCode}/{recordId}/compare")
    public ApiResponse<Map<String, Object>> compare(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @RequestParam("from") Integer fromVersion,
            @RequestParam("to") Integer toVersion) {
        return ApiResponse.success(service.compare(
                entityCode,
                recordId,
                fromVersion,
                toVersion));
    }
}
