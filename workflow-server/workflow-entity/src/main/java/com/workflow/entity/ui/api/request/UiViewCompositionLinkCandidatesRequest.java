package com.workflow.entity.ui.api.request;

import lombok.Data;

/** 请求服务端生成“选择候选记录建立关联”的受控列表上下文。 */
@Data
public class UiViewCompositionLinkCandidatesRequest {

    /** resolve 接口签发并绑定当前用户、宿主发布和来源记录的动作令牌。 */
    private String actionContextToken;
    /** LINK，或已发布配置中结果为 LINK 的 SELECT。 */
    private String action;
}
