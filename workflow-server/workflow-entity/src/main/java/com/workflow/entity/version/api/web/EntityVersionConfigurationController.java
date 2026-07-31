package com.workflow.entity.version.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher;
import com.workflow.entity.version.application.model.EntityVersionConfigSummary;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionReleaseSummary;
import com.workflow.entity.version.api.request.EntityVersionSimulationRequest;
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

/**
 * 数据版本策略管理接口。
 */
@RestController
@RequestMapping("/api/entity-versions/configs")
@RequiredArgsConstructor
@RequiresPermission("entity:version:config:list")
public class EntityVersionConfigurationController {

    private final EntityVersionConfigurationService service;
    private final EntityVersionPolicyMatcher matcher;

    @GetMapping
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<List<EntityVersionConfigSummary>> list(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.list(keyword));
    }

    @GetMapping("/{entityCode}")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<EntityVersionConfiguration> detail(
            @PathVariable String entityCode) {
        return ApiResponse.success(
                service.getDraft(entityCode));
    }

    @RequiresPermission("entity:version:config:update")
    @PostMapping("/{entityCode}/save")
    public ApiResponse<EntityVersionConfiguration> save(
            @PathVariable String entityCode,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(
                service.saveDraft(entityCode, request));
    }

    @RequiresPermission("entity:version:config:publish")
    @PostMapping("/{entityCode}/publish")
    public ApiResponse<EntityVersionConfiguration> publish(
            @PathVariable String entityCode) {
        return ApiResponse.success(
                service.publish(entityCode));
    }

    @GetMapping("/{entityCode}/releases")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<List<EntityVersionReleaseSummary>> releases(
            @PathVariable String entityCode) {
        return ApiResponse.success(
                service.releases(entityCode));
    }

    @PostMapping("/{entityCode}/simulate")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<Map<String, Object>> simulate(
            @PathVariable String entityCode,
            @RequestBody EntityVersionSimulationRequest request) {
        return ApiResponse.success(
                matcher.simulate(entityCode, request));
    }
}
