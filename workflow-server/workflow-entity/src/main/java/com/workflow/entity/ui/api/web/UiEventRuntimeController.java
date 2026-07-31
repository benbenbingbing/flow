package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.result.Result;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UI 事件运行时接口。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-runtime/events")
@RequiredArgsConstructor
public class UiEventRuntimeController {

    private final UiEventRuntimeService runtimeService;

    @PostMapping("/{eventCode}/execute")
    public Result<UiEventExecutionResult> execute(
            @PathVariable String eventCode,
            @RequestBody UiEventExecuteRequest request) {
        request.setEventCode(eventCode);
        return Result.success(runtimeService.execute(request));
    }
}
