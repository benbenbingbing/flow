package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.dto.UiDataSourceSaveRequest;
import com.workflow.entity.UiDataSourceDefinition;
import com.workflow.service.UiConfigurationAccessService;
import com.workflow.service.UiDataSourceService;
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
@RequestMapping("/api/ui-data-sources")
@RequiredArgsConstructor
public class UiDataSourceController {

    private final UiDataSourceService service;
    private final UiConfigurationAccessService accessService;

    @GetMapping("/catalog")
    public Result<Map<String, Object>> catalog() {
        return Result.success(service.catalog());
    }

    @GetMapping
    public Result<List<UiDataSourceDefinition>> list(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam(required = false) String sourceType) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.list(scopeType, scopeId, sourceType));
    }

    @PostMapping
    public Result<UiDataSourceDefinition> create(
            @RequestBody UiDataSourceSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(null);
        return Result.success(service.save(request));
    }

    @PostMapping("/{id}/update")
    public Result<UiDataSourceDefinition> update(
            @PathVariable String id,
            @RequestBody UiDataSourceSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(id);
        return Result.success(service.save(request));
    }

    @PostMapping("/{id}/delete")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestParam Integer expectedRevision) {
        accessService.requireGlobalConfigurationAccess();
        service.delete(id, expectedRevision);
        return Result.success();
    }

    @PostMapping("/{id}/preview")
    public Result<Object> preview(
            @PathVariable String id,
            @RequestBody UiDataSourceExecuteRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.preview(id, request));
    }

    @PostMapping("/{id}/execute")
    public Result<Object> execute(
            @PathVariable String id,
            @RequestBody UiDataSourceExecuteRequest request) {
        return Result.success(service.execute(id, request));
    }

    @PostMapping("/{id}/bindings/{usage}/validate")
    public Result<Map<String, Object>> validateBinding(
            @PathVariable String id,
            @PathVariable String usage) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.validateBinding(id, usage));
    }
}
