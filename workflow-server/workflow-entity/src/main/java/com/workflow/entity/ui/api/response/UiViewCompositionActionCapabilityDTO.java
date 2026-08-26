package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/** 关联内容中单个业务动作的服务端能力判定。 */
@Value
@Builder
public class UiViewCompositionActionCapabilityDTO {

    boolean available;
    String reason;

    /**
     * CREATE 可用时，由服务端根据当前来源记录和已发布字段映射生成的可信初值。
     * 其他动作始终为空，前端不得自行从来源行对象拼接初值。
     */
    @Builder.Default
    Map<String, Object> initialValues = Map.of();
}
