package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/** 服务端重新计算的关联候选列表上下文，不向浏览器暴露实际固定筛选。 */
@Value
@Builder
public class UiViewCompositionLinkCandidatesResponse {

    String action;
    String targetEntityCode;
    String targetContentId;
    String targetContentKey;
    String targetReleaseId;
    Integer targetReleaseVersion;
    com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest compositionContext;
    boolean matchNone;
}
