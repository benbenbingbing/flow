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

    /**
     * 列出人员解析器目录；查询结果供调用方展示或继续处理。
     *
     * @param usage 使用场景，作为 {@code Result.success} 的输入影响后续处理
     * @param keyword 关键字，作为 {@code Result.success} 的输入影响后续处理
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 符合条件的人员解析器选项结果，供调用方继续处理
     */
    @GetMapping
    public Result<List<PersonResolverOption>> list(
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return Result.success(service.listVisible(usage, keyword, limit));
    }

    /**
     * 处理{@code configs}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code configs}结果，供调用方继续处理
     */
    @GetMapping("/configs")
    public Result<List<PersonResolverOption>> configs() {
        return Result.success(service.listAllForAdmin());
    }

    /**
     * 保存人员解析器目录；后续读取或执行将使用更新后的状态。
     *
     * @param resolverCode 解析器编码，后续用于保存人员解析器目录时定位或关联目标
     * @param request 本次请求，后续经校验后用于保存人员解析器目录
     * @return 保存后的人员解析器目录结果，供调用方继续处理
     */
    @PostMapping("/configs/{resolverCode}")
    @RequiresPermission("system:extension:update")
    public Result<PersonResolverOption> save(
            @PathVariable String resolverCode,
            @Valid @RequestBody PersonResolverDefinitionRequest request) {
        return Result.success(service.save(resolverCode, request));
    }
}
