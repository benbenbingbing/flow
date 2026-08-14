package com.workflow.entity.mutationpolicy.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.mutationpolicy.application.EntityMutationCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Implementation catalog for the independent mutation-policy editor. */
@RestController
@RequestMapping("/api/entity-mutation-policies/catalog")
@RequiredArgsConstructor
@RequiresPermission("entity:mutation:config:list")
public class EntityMutationPolicyCatalogController {

    private final EntityMutationCatalogService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> catalog() {
        return ApiResponse.success(service.catalog());
    }

    @GetMapping("/options")
    public ApiResponse<PageResult<Map<String, Object>>> options(
            @RequestParam String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "8") long pageSize) {
        return ApiResponse.success(service.options(
                type, keyword, pageNum, pageSize));
    }
}
