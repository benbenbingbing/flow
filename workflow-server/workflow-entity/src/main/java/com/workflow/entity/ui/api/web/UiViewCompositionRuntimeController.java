package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import com.workflow.entity.ui.application.UiViewCompositionRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 关联内容运行时可信解析接口。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-runtime/view-compositions")
@RequiredArgsConstructor
public class UiViewCompositionRuntimeController {

    private final UiViewCompositionRuntimeService runtimeService;

    /**
     * 按宿主发布版本和来源记录解析目标表单记录或目标列表固定条件。
     */
    @PostMapping("/resolve")
    public Result<UiViewCompositionResolveResponse> resolve(
            @RequestBody UiViewCompositionResolveRequest request) {
        return Result.success(runtimeService.resolve(request));
    }
}
