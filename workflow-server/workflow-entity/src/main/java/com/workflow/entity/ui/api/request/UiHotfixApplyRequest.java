package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.time.LocalDateTime;

/** 创建受控 UI HOTFIX 申请。 */
@Data
public class UiHotfixApplyRequest {

    private String configType;
    private String configId;
    private String reason;
    private String ticketRef;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String expectedDraftHash;
    private String expectedActiveReleaseId;
    private String impactToken;
}
