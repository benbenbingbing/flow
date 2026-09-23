package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
import com.workflow.contracts.embed.runtime.context.EmbedDelegatedRequestContext;
import com.workflow.entity.ui.api.request.UiBoundExtensionExecuteRequest;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通过已验证 UI 绑定位置执行接口扩展。
 */
@AuthenticatedApi(objectAuthorization = true)
@EmbedDelegatedRuntimeApi(
        value = EmbedDelegatedRuntimeApi.Scope.FORM_OWNER_RUNTIME,
        targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FORM_OWNER_BODY)
@RestController
@RequestMapping("/api/ui-runtime/extensions")
@RequiredArgsConstructor
public class UiExtensionRuntimeController {

    /** 接口扩展定义、绑定授权和执行编排服务。 */
    private final UiInterfaceExtensionService service;

    /**
     * 执行界面扩展运行时，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于执行界面扩展运行时
     * @param servletRequest Servlet请求，供本方法执行界面扩展运行时时使用
     * @return 执行后的界面扩展运行时结果，供调用方继续处理
     */
    @PostMapping("/execute")
    public Result<Object> execute(
            @RequestBody UiBoundExtensionExecuteRequest request,
            HttpServletRequest servletRequest) {
        Object value = servletRequest.getAttribute(
                EmbedDelegatedRequestContext.TARGET_ATTRIBUTE);
        if (value instanceof EmbedDelegatedRequestContext.AuthorizedTarget
                target) {
            return Result.success(service.executeBoundOperationAtRelease(
                    request,
                    target.formReleaseId(),
                    target.formReleaseVersion()));
        }
        return Result.success(service.executeBoundOperation(request));
    }
}
