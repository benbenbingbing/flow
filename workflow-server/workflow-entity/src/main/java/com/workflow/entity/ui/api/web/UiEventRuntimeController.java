package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
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
@EmbedDelegatedRuntimeApi(
        value = EmbedDelegatedRuntimeApi.Scope.FORM_CONTEXT,
        targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FORM_EVENT_BODY)
@RestController
@RequestMapping("/api/ui-runtime/events")
@RequiredArgsConstructor
public class UiEventRuntimeController {

    private final UiEventRuntimeService runtimeService;

    /**
     * 执行界面事件运行时，并将结果传给后续步骤。
     *
     * @param eventCode 事件编码，后续用于执行界面事件运行时时定位或关联目标
     * @param request 本次请求，后续经校验后用于执行界面事件运行时
     * @return 执行后的界面事件运行时结果，供调用方继续处理
     */
    @PostMapping("/{eventCode}/execute")
    public Result<UiEventExecutionResult> execute(
            @PathVariable String eventCode,
            @RequestBody UiEventExecuteRequest request) {
        request.setEventCode(eventCode);
        return Result.success(runtimeService.execute(request));
    }
}
