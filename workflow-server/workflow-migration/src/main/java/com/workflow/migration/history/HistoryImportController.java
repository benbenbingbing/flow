package com.workflow.migration.history;

import com.workflow.contracts.history.HistoryImportModels.Plan;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

/** 历史导入管理接口；批次读取与修改均按真实登录用户验证所有权。 */
@RestController
@RequestMapping("/api/history-import")
@AuthenticatedApi(objectAuthorization = true)
@RequiredArgsConstructor
public class HistoryImportController {
    private final HistoryImportService service;
    public record Update(int revision, Plan plan) {}
    public record Revision(int revision) {}
    public record Execution(String validationHash, String groupKey, boolean retry) {}
    @GetMapping("/catalog") public ApiResponse<?> catalog() { return ApiResponse.success(service.catalog()); }
    @GetMapping("/batches") public ApiResponse<?> list(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return ApiResponse.success(service.list(page, size)); }
    @PostMapping("/batches") public ApiResponse<?> create(@RequestBody Plan plan) { return ApiResponse.success(service.create(plan)); }
    @GetMapping("/batches/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(service.get(id)); }
    @PostMapping("/batches/{id}/plan") public ApiResponse<?> update(@PathVariable String id, @RequestBody Update request) { return ApiResponse.success(service.updatePlan(id, request.revision(), request.plan())); }
    @GetMapping("/batches/{id}/templates/{kind}") public ResponseEntity<byte[]> template(@PathVariable String id, @PathVariable String kind) throws java.io.IOException {
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=history-" + ("entities".equals(kind) ? "entities" : "records") + ".xlsx")
                .header("Cache-Control", "private, no-store").header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").body(service.template(id, kind));
    }
    @PostMapping("/batches/{id}/files/{kind}") public ApiResponse<?> upload(@PathVariable String id, @PathVariable String kind, @RequestParam int revision, @RequestPart MultipartFile file) throws java.io.IOException {
        return ApiResponse.success(service.upload(id, revision, kind, file));
    }
    @PostMapping("/batches/{id}/validate") public ApiResponse<?> validate(@PathVariable String id, @RequestBody Revision request) { return ApiResponse.success(service.validate(id, request.revision())); }
    @PostMapping("/batches/{id}/execute") public ApiResponse<?> execute(@PathVariable String id, @RequestBody Execution request) { return ApiResponse.success(service.executeNext(id, request.validationHash(), request.groupKey(), request.retry())); }
}
