package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/** 前端执行动作前必须读取的服务端能力集合。 */
@Value
@Builder
public class UiViewCompositionActionCapabilitiesResponse {

    String sourceRecordId;
    @Builder.Default
    Map<String, UiViewCompositionActionCapabilityDTO> actionCapabilities =
            Map.of();
}
