package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * “关联内容”结构校验结果。
 */
@Value
@Builder
public class UiViewCompositionValidationDTO {

    boolean valid;
    /** 大小写和默认值均已规范化的配置，可直接用于后续保存。 */
    Map<String, Object> normalizedConfig;
    /** 面向配置人员的自然语言摘要。 */
    String summary;
    /** 非阻断性提示，例如独立保存边界。 */
    List<String> warnings;
}
