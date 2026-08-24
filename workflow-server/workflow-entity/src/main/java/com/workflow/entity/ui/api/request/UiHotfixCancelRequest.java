package com.workflow.entity.ui.api.request;

import lombok.Data;

/** 取消尚未发布的 UI HOTFIX 申请。 */
@Data
public class UiHotfixCancelRequest {

    private String reason;
}
