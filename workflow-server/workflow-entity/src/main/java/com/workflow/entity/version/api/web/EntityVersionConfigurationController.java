package com.workflow.entity.version.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher;
import com.workflow.entity.version.application.EntityVersionScopePreviewService;
import com.workflow.entity.version.application.model.EntityVersionConfigSummary;
import com.workflow.entity.version.application.model.EntityVersionConfigReleaseSummary;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionScopePreview;
import com.workflow.entity.version.application.model.EntityVersionValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.version.api.request.EntityVersionSimulationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final EntityVersionScopePreviewService previewService;
    private final ObjectMapper objectMapper;

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

    @GetMapping("/{entityCode}/draft")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<EntityVersionConfiguration> draft(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.getDraft(entityCode));
    }

    @PutMapping("/{entityCode}/draft")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionConfiguration> saveDraft(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.saveDraft(
                entityCode, request, revision(ifMatch)));
    }

    @PostMapping("/{entityCode}/validate")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionValidationResult> validate(
            @PathVariable String entityCode,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.validateDraft(entityCode, request));
    }

    @PostMapping("/{entityCode}/scope-preview")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionScopePreview> scopePreview(
            @PathVariable String entityCode,
            @RequestBody Map<String, Object> body) {
        Object draftValue = body.get("draft");
        EntityVersionConfiguration request = objectMapper.convertValue(
                draftValue == null ? withoutPreviewKeys(body) : draftValue,
                EntityVersionConfiguration.class);
        String recordId = text(body.get("recordId"));
        if (recordId == null) {
            recordId = text(body.get("previewRecordId"));
        }
        return ApiResponse.success(previewService.preview(
                entityCode, request, recordId));
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
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.success(
                service.publish(entityCode, revision(ifMatch)));
    }

    @RequiresPermission("entity:version:config:publish")
    @PostMapping("/{entityCode}/releases")
    public ApiResponse<EntityVersionConfiguration> createRelease(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.success(service.publish(
                entityCode, revision(ifMatch)));
    }

    @GetMapping("/{entityCode}/releases")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<PageResult<EntityVersionConfigReleaseSummary>> releases(
            @PathVariable String entityCode,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(
                service.releases(entityCode, pageNum, pageSize));
    }

    @PostMapping("/{entityCode}/simulate")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<Map<String, Object>> simulate(
            @PathVariable String entityCode,
            @RequestBody EntityVersionSimulationRequest request) {
        return ApiResponse.success(
                matcher.simulate(entityCode, request));
    }

    private Integer revision(String value) {
        if (value == null) {
            throw new IllegalArgumentException("If-Match不能为空");
        }
        String normalized = value.trim()
                .replace("W/", "")
                .replace("\"", "");
        return integer(normalized);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("草稿修订号格式错误");
        }
    }

    private Map<String, Object> withoutPreviewKeys(
            Map<String, Object> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(source);
        result.remove("recordId");
        result.remove("previewRecordId");
        return result;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
