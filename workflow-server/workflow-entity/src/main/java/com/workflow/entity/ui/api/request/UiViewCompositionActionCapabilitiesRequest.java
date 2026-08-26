package com.workflow.entity.ui.api.request;

import lombok.Data;

/** 查询关联内容权威动作能力的请求。 */
@Data
public class UiViewCompositionActionCapabilitiesRequest {

    /** resolve 接口签发的短期动作上下文。 */
    private String actionContextToken;
}
