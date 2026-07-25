package com.workflow.dto;

import lombok.Data;

/**
 * UI 配置发布请求。
 */
@Data
public class UiConfigPublishRequest {

    /** 发布说明 */
    private String description;
    /** 发布模式：STANDARD/HOTFIX，旧客户端缺省为 STANDARD */
    private String releaseMode;
    /** 热修复生效范围，首期固定 ACTIVE_AND_FUTURE */
    private String rolloutScope;
    /** 预检时看到的当前激活发布ID */
    private String expectedActiveReleaseId;
    /** 预检时看到的草稿内容哈希 */
    private String expectedDraftHash;
    /** 发布影响预检令牌 */
    private String impactToken;
    /** 是否经授权覆盖 REVIEW 风险 */
    private Boolean overrideRisk;
    /** 风险覆盖原因 */
    private String overrideReason;
}
