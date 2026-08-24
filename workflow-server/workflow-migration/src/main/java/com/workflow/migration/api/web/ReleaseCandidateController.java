package com.workflow.migration.api.web;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.migration.api.request.ReleaseCandidateCompensateRequest;
import com.workflow.migration.api.request.ReleaseCandidateCreateRequest;
import com.workflow.migration.api.request.ReleaseCandidateExecuteRequest;
import com.workflow.migration.api.request.ReleaseCandidatePreflightRequest;
import com.workflow.migration.application.ReleaseCandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 应用级发布候选 API。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/release-candidates")
@RequiredArgsConstructor
public class ReleaseCandidateController {

    private final ReleaseCandidateService candidateService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        require("release-candidate:list");
        return ApiResponse.success(candidateService.list());
    }

    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> sources() {
        require("release-candidate:create");
        return ApiResponse.success(candidateService.sources());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        require("release-candidate:list");
        return ApiResponse.success(candidateService.get(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @Valid @RequestBody ReleaseCandidateCreateRequest request) {
        require("release-candidate:create");
        return ApiResponse.success(candidateService.create(request));
    }

    @PostMapping("/{id}/preflight")
    public ApiResponse<Map<String, Object>> preflight(
            @PathVariable String id,
            @Valid @RequestBody ReleaseCandidatePreflightRequest request) {
        require("release-candidate:create");
        return ApiResponse.success(candidateService.preflight(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(
            @PathVariable String id,
            @Valid @RequestBody ReleaseCandidateExecuteRequest request) {
        require("release-candidate:publish");
        return ApiResponse.success(candidateService.publish(id, request));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<Map<String, Object>> resume(
            @PathVariable String id,
            @Valid @RequestBody ReleaseCandidateExecuteRequest request) {
        require("release-candidate:recover");
        return ApiResponse.success(candidateService.resume(id, request));
    }

    @PostMapping("/{id}/compensate")
    public ApiResponse<Map<String, Object>> compensate(
            @PathVariable String id,
            @Valid @RequestBody ReleaseCandidateCompensateRequest request) {
        require("release-candidate:recover");
        return ApiResponse.success(candidateService.compensate(id, request));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> report(@PathVariable String id) {
        require("release-candidate:list");
        ReleaseCandidateService.ReportFile report = candidateService.report(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(report.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(report.data().length);
        return ResponseEntity.ok().headers(headers).body(report.data());
    }

    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限执行发布候选操作：" + permission);
        }
    }
}
