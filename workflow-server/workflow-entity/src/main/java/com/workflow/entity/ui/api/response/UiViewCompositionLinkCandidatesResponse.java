package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/** 服务端签发的关联候选列表上下文，不向浏览器暴露实际固定筛选。 */
@Value
@Builder
public class UiViewCompositionLinkCandidatesResponse {

    String action;
    String targetEntityCode;
    String targetContentId;
    String targetContentKey;
    String targetReleaseId;
    Integer targetReleaseVersion;
    /** 只能用于读取候选列表并在 LINK 动作时验证目标范围的短期令牌。 */
    String candidateListContextToken;
    boolean matchNone;
}
