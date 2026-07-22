package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.UiComponentTemplateSaveRequest;
import com.workflow.dto.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.UiComponentTemplate;
import com.workflow.entity.UiComponentTemplateVersion;
import com.workflow.service.UiComponentTemplateService;
import com.workflow.service.UiConfigurationAccessService;
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

@RestController
@RequestMapping("/api/ui-component-templates")
@RequiredArgsConstructor
public class UiComponentTemplateController {

    private final UiComponentTemplateService service;
    private final UiConfigurationAccessService accessService;

    @GetMapping
    public Result<List<UiComponentTemplate>> list(
            @RequestParam(required = false) String templateType) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.list(templateType));
    }

    @PostMapping
    public Result<UiComponentTemplate> save(
            @RequestBody UiComponentTemplateSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.save(request));
    }

    @GetMapping("/{id}/versions")
    public Result<List<UiComponentTemplateVersion>> versions(
            @PathVariable String id) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.versions(id));
    }

    @PostMapping("/{id}/versions")
    public Result<UiComponentTemplateVersion> createVersion(
            @PathVariable String id,
            @RequestBody UiComponentTemplateSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.createVersion(
                id, request.getSnapshot(), request.getDescription()));
    }

    @PostMapping("/{id}/upgrade")
    public Result<Map<String, Object>> upgrade(
            @PathVariable String id,
            @RequestBody UiComponentTemplateUpgradeRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.upgrade(id, request));
    }
}
