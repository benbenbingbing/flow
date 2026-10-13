package com.workflow.entity.ui.api.request;

import lombok.Data;

/** 查询关联内容权威动作能力的请求。 */
@Data
public class UiViewCompositionActionCapabilitiesRequest {

    /** 从业务根入口逐层重新校验的关联路径。 */
    private UiViewCompositionResolveRequest compositionContext;
}
