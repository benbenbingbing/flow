package com.workflow.admin.extension.person.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.admin.extension.person.api.request.PersonResolverDefinitionRequest;
import com.workflow.admin.extension.person.api.response.PersonResolverOption;
import com.workflow.admin.extension.person.application.PersonResolverCatalogService;
import com.workflow.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受控人员解析器目录接口。
 */
@RequiresPermission("system:extension:list")
@RestController
@RequestMapping("/api/person-resolvers")
@RequiredArgsConstructor
public class PersonResolverCatalogController {

    private final PersonResolverCatalogService service;

    @GetMapping
    public Result<List<PersonResolverOption>> list(
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return Result.success(service.listVisible(usage, keyword, limit));
    }

    @GetMapping("/configs")
    public Result<List<PersonResolverOption>> configs() {
        return Result.success(service.listAllForAdmin());
    }

    @PostMapping("/configs/{resolverCode}")
    @RequiresPermission("system:extension:update")
    public Result<PersonResolverOption> save(
            @PathVariable String resolverCode,
            @Valid @RequestBody PersonResolverDefinitionRequest request) {
        return Result.success(service.save(resolverCode, request));
    }
}
