package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/** 关联内容中单个业务动作的服务端能力判定。 */
@Value
@Builder
public class UiViewCompositionActionCapabilityDTO {

    boolean available;
    String reason;
}
