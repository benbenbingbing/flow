package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
import com.workflow.entity.ui.api.request.UiViewCompositionActionCapabilitiesRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionActionRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilitiesResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionActionResponse;
import com.workflow.entity.ui.application.UiViewCompositionActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已发布“关联内容”的权威业务动作入口。 */
@AuthenticatedApi(objectAuthorization = true)
@EmbedDelegatedRuntimeApi(
        value = EmbedDelegatedRuntimeApi.Scope.RUNTIME_CONTEXT,
        targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                .RUNTIME_CONTEXT)
@RestController
@RequestMapping("/api/ui-runtime/view-compositions/actions")
@RequiredArgsConstructor
public class UiViewCompositionActionController {

    private final UiViewCompositionActionService actionService;

    /**
     * 执行已发布接口扩展动作。实体、字段和筛选条件均由服务端重新校验已发布关联配置后恢复。
     *
     * @param request 本次请求，后续经校验后用于执行界面视图组合动作
     * @return 执行后的界面视图组合动作结果，供调用方继续处理
     */
    @PostMapping
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.RUNTIME_CONTEXT,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                    .RUNTIME_CONTEXT)
    public Result<UiViewCompositionActionResponse> execute(
            @RequestBody UiViewCompositionActionRequest request) {
        return Result.success(actionService.execute(request));
    }

    /**
     * 返回服务端权威能力；前端不得把发布文档中的原始 actions 直接当作授权。
     *
     * @param request 本次请求，后续经校验后用于处理能力集合
     * @return 处理后的能力集合结果，供调用方继续处理
     */
    @PostMapping("/capabilities")
    public Result<UiViewCompositionActionCapabilitiesResponse> capabilities(
            @RequestBody UiViewCompositionActionCapabilitiesRequest request) {
        return Result.success(actionService.capabilities(request));
    }

}
