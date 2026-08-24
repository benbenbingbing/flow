package com.workflow.process.configintelligence.api.web;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.Blueprint;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.BlueprintInstance;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.BlueprintSaveRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.DependencyEdge;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.DependencySaveRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.ImpactReport;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.ImpactRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.InstantiateRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.PackageVerification;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.PortablePackage;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.QualityAnalyzeRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.QualityReport;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 配置资产智能中心 API。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/config-intelligence")
@RequiredArgsConstructor
public class ConfigurationIntelligenceController {

    private final ConfigurationIntelligenceService service;

    @GetMapping("/blueprints")
    public Result<List<Blueprint>> blueprints(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        require("config:intelligence:list");
        return Result.success(service.blueprints(category, status));
    }

    @GetMapping("/blueprints/{id}")
    public Result<Blueprint> blueprint(@PathVariable String id) {
        require("config:intelligence:list");
        return Result.success(service.blueprint(id));
    }

    @PostMapping("/blueprints")
    public Result<Blueprint> saveBlueprint(@RequestBody BlueprintSaveRequest request) {
        require("config:intelligence:manage");
        return Result.success(service.saveBlueprint(request));
    }

    @PostMapping("/blueprints/{id}/publish")
    public Result<Blueprint> publishBlueprint(@PathVariable String id) {
        require("config:intelligence:manage");
        return Result.success(service.publishBlueprint(id));
    }

    @PostMapping("/blueprints/{id}/instantiate")
    public Result<BlueprintInstance> instantiate(
            @PathVariable String id,
            @RequestBody(required = false) InstantiateRequest request) {
        require("config:intelligence:manage");
        return Result.success(service.instantiate(
                id, request == null ? java.util.Map.of() : request.parameters()));
    }

    @PostMapping("/blueprints/{id}/package")
    public Result<PortablePackage> portablePackage(
            @PathVariable String id,
            @RequestBody(required = false) InstantiateRequest request) {
        require("config:intelligence:manage");
        return Result.success(service.portablePackage(
                id, request == null ? java.util.Map.of() : request.parameters()));
    }

    @PostMapping("/packages/verify")
    public Result<PackageVerification> verifyPackage(@RequestBody PortablePackage configPackage) {
        require("config:intelligence:analyze");
        return Result.success(service.verifyPackage(configPackage));
    }

    @GetMapping("/dependencies/{sourceType}/{sourceId}")
    public Result<List<DependencyEdge>> dependencies(
            @PathVariable String sourceType,
            @PathVariable String sourceId) {
        require("config:intelligence:list");
        return Result.success(service.dependencies(sourceType, sourceId));
    }

    @PostMapping("/dependencies/{sourceType}/{sourceId}")
    public Result<List<DependencyEdge>> replaceDependencies(
            @PathVariable String sourceType,
            @PathVariable String sourceId,
            @RequestBody List<DependencySaveRequest> dependencies) {
        require("config:intelligence:manage");
        return Result.success(service.replaceDependencies(sourceType, sourceId, dependencies));
    }

    @PostMapping("/impact")
    public Result<ImpactReport> impact(@RequestBody ImpactRequest request) {
        require("config:intelligence:analyze");
        return Result.success(service.impact(request));
    }

    @PostMapping("/quality/analyze")
    public Result<QualityReport> analyzeQuality(@RequestBody QualityAnalyzeRequest request) {
        require("config:intelligence:analyze");
        return Result.success(service.analyzeQuality(request));
    }

    @GetMapping("/quality/{assetType}/{assetId}")
    public Result<List<QualityReport>> qualityHistory(
            @PathVariable String assetType,
            @PathVariable String assetId) {
        require("config:intelligence:list");
        return Result.success(service.qualityHistory(assetType, assetId));
    }

    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("缺少权限: " + permission);
        }
    }
}
