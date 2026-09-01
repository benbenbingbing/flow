package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.EmbedDelegatedRuntimeApi;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.workflow.entity.ui.api.request.UiInterfaceOperationExecuteRequest;
import com.workflow.entity.ui.application.UiDataSourceService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通过已验证 UI 绑定位置执行接口操作。
 */
@AuthenticatedApi(objectAuthorization = true)
@EmbedDelegatedRuntimeApi(
        value = EmbedDelegatedRuntimeApi.Scope.FORM_OWNER_RUNTIME,
        targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FORM_OWNER_BODY)
@RestController
@RequestMapping("/api/ui-runtime/interface-operations")
@RequiredArgsConstructor
public class UiInterfaceOperationRuntimeController {

    /** 接口服务定义、绑定授权和执行编排服务。 */
    private final UiDataSourceService service;

    @PostMapping("/execute")
    public Result<Object> execute(
            @RequestBody UiInterfaceOperationExecuteRequest request,
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
