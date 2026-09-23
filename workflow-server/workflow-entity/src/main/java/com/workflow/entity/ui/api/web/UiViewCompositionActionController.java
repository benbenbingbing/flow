package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
import com.workflow.entity.ui.api.request.UiViewCompositionActionCapabilitiesRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionActionRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionLinkCandidatesRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilitiesResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionActionResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionLinkCandidatesResponse;
import com.workflow.entity.ui.application.UiViewCompositionActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已发布“关联内容”的权威业务动作入口。 */
@AuthenticatedApi(objectAuthorization = true)
@EmbedDelegatedRuntimeApi(
        value = EmbedDelegatedRuntimeApi.Scope.SIGNED_RUNTIME_CONTEXT,
        targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                .SIGNED_RUNTIME_CONTEXT)
@RestController
@RequestMapping("/api/ui-runtime/view-compositions/actions")
@RequiredArgsConstructor
public class UiViewCompositionActionController {

    private final UiViewCompositionActionService actionService;

    /**
     * 执行 SELECT、LINK 或 UNLINK。实体、字段和筛选条件只从签名发布上下文恢复。
     *
     * @param request 本次请求，后续经校验后用于执行界面视图组合动作
     * @return 执行后的界面视图组合动作结果，供调用方继续处理
     */
    @PostMapping
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.SIGNED_RUNTIME_CONTEXT,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                    .SIGNED_RUNTIME_CONTEXT)
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

    /**
     * 生成“选择并建立关联”的专用候选列表上下文。候选条件完全来自已发布配置，
     * 请求不能携带实体、列表或任意筛选。
     *
     * @param request 本次请求，后续经校验后用于处理链接候选集合
     * @return 处理后的链接候选集合结果，供调用方继续处理
     */
    @PostMapping("/link-candidates")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.SIGNED_RUNTIME_CONTEXT,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                    .SIGNED_RUNTIME_CONTEXT)
    public Result<UiViewCompositionLinkCandidatesResponse> linkCandidates(
            @RequestBody UiViewCompositionLinkCandidatesRequest request) {
        return Result.success(actionService.linkCandidates(request));
    }
}
