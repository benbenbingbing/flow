package com.workflow.process.migration.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.CreateBatchRequest;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.ExecuteBatchRequest;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.RetryBatchRequest;
import com.workflow.process.migration.application.ProcessInstanceMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 流程实例迁移的干运行、分批执行、暂停、续跑和受限回迁入口。 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/process-instance-migrations")
@RequiredArgsConstructor
public class ProcessInstanceMigrationController {

    private final ProcessInstanceMigrationService service;

    @GetMapping
    @RequiresPermission("process-instance-migration:preview")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("process-instance-migration:preview")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    @RequiresPermission("process-instance-migration:preview")
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateBatchRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping("/{id}/dry-run")
    @RequiresPermission("process-instance-migration:preview")
    public ApiResponse<Map<String, Object>> dryRun(@PathVariable String id) {
        return ApiResponse.success(service.dryRun(id));
    }

    @PostMapping("/{id}/execute")
    @RequiresPermission("process-instance-migration:execute")
    public ApiResponse<Map<String, Object>> execute(
            @PathVariable String id,
            @RequestBody ExecuteBatchRequest request) {
        return ApiResponse.success(service.execute(id, request));
    }

    @PostMapping("/{id}/pause")
    @RequiresPermission("process-instance-migration:execute")
    public ApiResponse<Map<String, Object>> pause(@PathVariable String id) {
        return ApiResponse.success(service.pause(id));
    }

    @PostMapping("/{id}/retry")
    @RequiresPermission("process-instance-migration:execute")
    public ApiResponse<Map<String, Object>> retry(
            @PathVariable String id,
            @RequestBody RetryBatchRequest request) {
        return ApiResponse.success(service.retry(id, request));
    }

    @PostMapping("/items/{id}/rollback")
    @RequiresPermission("process-instance-migration:execute")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String id) {
        return ApiResponse.success(service.rollbackItem(id));
    }
}
