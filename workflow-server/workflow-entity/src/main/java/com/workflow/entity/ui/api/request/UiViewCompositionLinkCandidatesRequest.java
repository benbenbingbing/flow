package com.workflow.entity.ui.api.request;

import lombok.Data;

/** 请求服务端生成“选择候选记录建立关联”的受控列表上下文。 */
@Data
public class UiViewCompositionLinkCandidatesRequest {

    /** 从业务根入口逐层重新校验的关联路径。 */
    private UiViewCompositionResolveRequest compositionContext;
    /** LINK，或已发布配置中结果为 LINK 的 SELECT。 */
    private String action;
}
