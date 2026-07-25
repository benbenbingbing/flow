package com.workflow.dto;

import lombok.Data;

/**
 * UI 配置热修复回滚请求。
 */
@Data
public class UiConfigHotfixRollbackRequest {

    /** 回滚原因 */
    private String reason;
}
