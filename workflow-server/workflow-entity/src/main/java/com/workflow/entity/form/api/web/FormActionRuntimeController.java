package com.workflow.entity.form.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.application.EntityFormActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表单按钮运行时接口。
 */
@AuthenticatedApi(objectAuthorization = true)
@EmbedDelegatedRuntimeApi(
        value = EmbedDelegatedRuntimeApi.Scope.FORM_CONTEXT,
        targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                .FORM_ACTION_BODY)
@RestController
@RequestMapping("/api/ui-runtime/form-actions")
@RequiredArgsConstructor
public class FormActionRuntimeController {

    private final EntityFormActionService actionService;

    /**
     * 解析表单动作运行时；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析表单动作运行时
     * @return 解析后的表单动作运行时结果，供调用方继续处理
     */
    @PostMapping("/resolve")
    public Result<List<FormActionRuntimeDTO>> resolve(
            @RequestBody FormActionResolveRequest request) {
        // Embed 复用 Flow 原生按钮求值；用户权限、行级规则和数据范围
        // 已在 actionService 内权威计算，不再叠加 Embed 专用按钮白名单。
        return Result.success(actionService.resolve(request));
    }
}
