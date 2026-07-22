package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.UiExtensionDefinition;
import com.workflow.service.UiConfigurationAccessService;
import com.workflow.service.UiExtensionDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ui-extensions")
@RequiredArgsConstructor
public class UiExtensionDefinitionController {

    private final UiExtensionDefinitionService service;
    private final UiConfigurationAccessService accessService;

    @GetMapping
    public Result<List<UiExtensionDefinition>> list(
            @RequestParam(required = false) String extensionType,
            @RequestParam(required = false) String extensionKey,
            @RequestParam(required = false) String status) {
        return Result.success(service.list(
                extensionType, extensionKey, status));
    }

    @PostMapping
    public Result<UiExtensionDefinition> create(
            @RequestBody UiExtensionDefinitionSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(null);
        return Result.success(service.save(request));
    }

    @PutMapping("/{id}")
    public Result<UiExtensionDefinition> update(
            @PathVariable String id,
            @RequestBody UiExtensionDefinitionSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(id);
        return Result.success(service.save(request));
    }
}
