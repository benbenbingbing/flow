package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 接口服务事件引用在一个具体 FORM/LIST 发布上下文中的状态。
 */
@Value
@Builder
public class UiDataSourceReferenceContextDTO {

    String configType;
    String configId;
    String configKey;
    String configName;
    String releaseId;
    Integer releaseVersion;
    boolean activeReleasePresent;
    String publicationStatus;
    String publicationReason;
    boolean published;
    Boolean draftMatchesPublished;
    boolean draftStepPublished;
    Boolean draftStepEffective;
    Boolean publishedStepEffective;
    String publishedMatchBasis;
    String effectiveStatus;
    String effectiveReason;
    List<UiDataSourceReferenceEffectiveStepDTO> effectiveChain;
}
