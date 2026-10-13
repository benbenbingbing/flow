package com.workflow.entity.excel.api.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.excel.runtime.EntityExcelFileStore;
import com.workflow.entity.excel.runtime.EntityExcelJobService;
import com.workflow.entity.excel.runtime.EntityExcelRuntimeResolver;
import com.workflow.entity.excel.runtime.ExcelPrepareRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 默认与自定义 Excel 入口共用的 GET/POST 协议；对象授权在任务与执行计划服务中完成。 */
@RestController
@RequestMapping("/api")
@AuthenticatedApi(objectAuthorization = true)
@RequiredArgsConstructor
public class EntityExcelRuntimeController {
    private final EntityExcelRuntimeResolver resolver;
    private final EntityExcelJobService jobs;

    @PostMapping("/entity-excel/actions/prepare")
    public ApiResponse<?> prepare(@RequestBody ExcelPrepareRequest request) { return ApiResponse.success(resolver.prepare(request)); }

    @PostMapping("/entity-excel/templates/download")
    public void template(@RequestBody ActionRequest request, HttpServletResponse response) throws IOException {
        var plan = resolver.resolve(request.actionContextToken(), "TEMPLATE");
        downloadHeaders(response, EntityExcelRuntimeResolver.safeName(plan.scheme().document().name()) + "_导入模板.xlsx", EntityExcelFileStore.XLSX);
        jobs.template(request.actionContextToken(), response.getOutputStream());
    }

    @PostMapping(value = "/entity-excel/import-checks", consumes = "multipart/form-data")
    public ApiResponse<?> check(@RequestParam String actionContextToken, @RequestParam String requestId,
            @RequestParam MultipartFile file) throws IOException {
        return ApiResponse.success(jobs.importFile(actionContextToken, requestId, file, true));
    }

    @PostMapping(value = "/entity-excel/imports", consumes = "multipart/form-data")
    public ApiResponse<?> importFile(@RequestParam String actionContextToken, @RequestParam String requestId,
            @RequestParam MultipartFile file) throws IOException {
        return ApiResponse.success(jobs.importFile(actionContextToken, requestId, file, false));
    }

    @PostMapping("/entity-excel/exports")
    public ApiResponse<?> export(@RequestBody ActionRequest request) throws IOException {
        return ApiResponse.success(jobs.export(request.actionContextToken(), request.requestId(), request.recordIds(), request.filters()));
    }

    @GetMapping("/entity-excel-jobs/{jobId}")
    public ApiResponse<?> get(@PathVariable String jobId) { return ApiResponse.success(jobs.get(jobId)); }

    @GetMapping("/entity-excel-jobs/{jobId}/errors")
    public ApiResponse<?> errors(@PathVariable String jobId, @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) { return ApiResponse.success(jobs.errors(jobId, pageNum, pageSize)); }

    @GetMapping("/entity-excel-jobs/{jobId}/files/{fileKind}")
    public void file(@PathVariable String jobId, @PathVariable String fileKind, HttpServletResponse response) throws IOException {
        try (var file = jobs.download(jobId, fileKind)) {
            downloadHeaders(response, file.filename(), file.contentType());
            response.setContentLengthLong(file.contentLength());
            file.inputStream().transferTo(response.getOutputStream());
        }
    }

    private static void downloadHeaders(HttpServletResponse response, String filename, String contentType) {
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString());
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    public record ActionRequest(@JsonAlias("token") String actionContextToken, String requestId,
                                @JsonAlias("selectedIds") List<String> recordIds, Map<String, Object> filters) { }
}
