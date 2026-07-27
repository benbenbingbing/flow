package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.PersonResolverDefinitionRequest;
import com.workflow.dto.PersonResolverOptionDTO;
import com.workflow.service.PersonResolverDefinitionService;
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
@RestController
@RequestMapping("/api/person-resolvers")
@RequiredArgsConstructor
public class PersonResolverController {

    private final PersonResolverDefinitionService service;

    @GetMapping
    public Result<List<PersonResolverOptionDTO>> list(
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return Result.success(service.listVisible(usage, keyword, limit));
    }

    @GetMapping("/configs")
    public Result<List<PersonResolverOptionDTO>> configs() {
        return Result.success(service.listAllForAdmin());
    }

    @PostMapping("/configs/{resolverCode}")
    public Result<PersonResolverOptionDTO> save(
            @PathVariable String resolverCode,
            @Valid @RequestBody PersonResolverDefinitionRequest request) {
        return Result.success(service.save(resolverCode, request));
    }
}
