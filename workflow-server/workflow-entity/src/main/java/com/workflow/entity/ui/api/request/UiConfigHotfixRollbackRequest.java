package com.workflow.entity.ui.api.request;

import lombok.Data;

/**
 * UI 配置热修复回滚请求。
 */
@Data
public class UiConfigHotfixRollbackRequest {

    /** 回滚原因 */
    private String reason;
}
