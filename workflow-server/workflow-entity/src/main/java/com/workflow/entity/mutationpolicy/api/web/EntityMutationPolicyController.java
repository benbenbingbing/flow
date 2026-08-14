package com.workflow.entity.mutationpolicy.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.mutationpolicy.application.EntityMutationPolicyService;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicyDocument;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicySummary;
import com.workflow.entity.version.application.model.EntityVersionReleaseSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/entity-mutation-policies/configs")
@RequiredArgsConstructor
@RequiresPermission("entity:mutation:config:list")
public class EntityMutationPolicyController {

    private final EntityMutationPolicyService service;

    @GetMapping
    public ApiResponse<List<EntityMutationPolicySummary>> list(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.list(keyword));
    }

    @GetMapping("/{entityCode}/draft")
    public ApiResponse<EntityMutationPolicyDocument> draft(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.getDraft(entityCode));
    }

    @PutMapping("/{entityCode}/draft")
    @RequiresPermission("entity:mutation:config:update")
    public ApiResponse<EntityMutationPolicyDocument> saveDraft(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody EntityMutationPolicyDocument request) {
        return ApiResponse.success(service.saveDraft(
                entityCode,
                request,
                revision(ifMatch)));
    }

    @PostMapping("/{entityCode}/releases")
    @RequiresPermission("entity:mutation:config:publish")
    public ApiResponse<EntityMutationPolicyDocument> publish(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.success(service.publish(
                entityCode,
                revision(ifMatch)));
    }

    @GetMapping("/{entityCode}/releases")
    public ApiResponse<List<EntityVersionReleaseSummary>> releases(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.releases(entityCode));
    }

    private Integer revision(String ifMatch) {
        String normalized = ifMatch.trim()
                .replace("W/", "")
                .replace("\"", "");
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException(
                    "If-Match 必须是草稿修订号");
        }
    }
}
