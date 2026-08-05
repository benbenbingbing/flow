package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.ui.api.request.UiDataSourceDeleteRequest;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统一接口事件绑定管理与运行控制器。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-event-bindings")
@RequiredArgsConstructor
public class UiEventBindingController {

    private final UiEventBindingService bindingService;

    @GetMapping("/catalog")
    public Result<Map<String, Object>> catalog() {
        return Result.success(bindingService.catalog());
    }

    @GetMapping
    public Result<List<UiEventBinding>> list(
            @RequestParam String ownerType,
            @RequestParam String ownerId) {
        return Result.success(bindingService.list(ownerType, ownerId));
    }

    @GetMapping("/resolved-draft")
    public Result<Map<String, Object>> resolveDraft(
            @RequestParam String ownerType,
            @RequestParam String ownerId,
            @RequestParam String eventCode) {
        return Result.success(bindingService.resolveDraft(
                ownerType,
                ownerId,
                eventCode));
    }

    @PostMapping
    public Result<UiEventBinding> create(
            @RequestBody UiEventBindingSaveRequest request) {
        request.setId(null);
        return Result.success(bindingService.save(request));
    }

    @PostMapping("/{id}/update")
    public Result<UiEventBinding> update(
            @PathVariable String id,
            @RequestBody UiEventBindingSaveRequest request) {
        request.setId(id);
        return Result.success(bindingService.save(request));
    }

    @PostMapping("/{id}/delete")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestBody UiDataSourceDeleteRequest request) {
        bindingService.delete(id, request.getExpectedRevision());
        return Result.success();
    }

}
