package com.workflow.entity.ui.api.request;

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
    /** 兼容旧客户端保留，REVIEW 已不再需要风险覆盖 */
    private Boolean overrideRisk;
    /** 兼容旧客户端保留，不再参与热修复发布判断 */
    private String overrideReason;
}
