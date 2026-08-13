package com.workflow.entity.ui.api.request;

import lombok.Data;

/**
 * UI 发布版本切换或草稿恢复请求。
 */
@Data
public class UiConfigReleaseOperationRequest {

    /** 操作原因，用于发布审计。 */
    private String reason;

    /** 预览时观察到的当前激活版本，用于防止确认期间发生并发切换。 */
    private String expectedActiveReleaseId;
}
