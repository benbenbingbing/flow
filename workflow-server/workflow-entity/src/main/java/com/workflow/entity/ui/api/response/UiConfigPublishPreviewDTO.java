package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * UI 配置发布预检结果。
 */
@Value
@Builder
public class UiConfigPublishPreviewDTO {

    String configType;
    String configId;
    String releaseMode;
    String rolloutScope;
    String draftHash;
    String activeReleaseId;
    Integer activeVersion;
    String targetHash;
    String impactToken;
    String riskLevel;
    boolean changed;
    /** 兼容旧客户端保留，当前固定为 false */
    boolean requiresOverride;
    boolean canPublish;
    int processVersionCount;
    long activeInstanceCount;
    long skippedHistoricalInstanceCount;
    List<UiConfigDiffItemDTO> changedItems;
    List<UiConfigRiskItemDTO> riskItems;
    /** 旧协议兼容空集合；统一发布不再按流程版本划分生效目标。 */
    List<Map<String, Object>> targets;
    /** 发布后会被精确固定的关联内容依赖，供配置人员发布前确认。 */
    List<Map<String, Object>> dependencies;
    List<String> blockers;
}
