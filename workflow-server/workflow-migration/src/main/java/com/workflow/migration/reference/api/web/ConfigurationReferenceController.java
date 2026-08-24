package com.workflow.migration.reference.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.migration.reference.application.ConfigurationReferenceModels.ImpactReport;
import com.workflow.migration.reference.application.ConfigurationReferenceModels.ReferenceEdge;
import com.workflow.migration.reference.application.ConfigurationReferenceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 配置资产正反向引用、路径和影响分析入口。 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/config-references")
@RequiredArgsConstructor
@RequiresPermission("config-reference:list")
public class ConfigurationReferenceController {

    private final ConfigurationReferenceQueryService service;

    @GetMapping("/forward")
    public ApiResponse<List<ReferenceEdge>> forward(
            @RequestParam String type,
            @RequestParam String key,
            @RequestParam(required = false) Integer version) {
        return ApiResponse.success(service.forward(type, key, version));
    }

    @GetMapping("/reverse")
    public ApiResponse<List<ReferenceEdge>> reverse(
            @RequestParam String type,
            @RequestParam String key) {
        return ApiResponse.success(service.reverse(type, key));
    }

    @GetMapping("/impact")
    public ApiResponse<ImpactReport> impact(
            @RequestParam String type,
            @RequestParam String key,
            @RequestParam(defaultValue = "BOTH") String direction,
            @RequestParam(defaultValue = "8") Integer maxDepth) {
        return ApiResponse.success(service.impact(type, key, direction, maxDepth));
    }
}
