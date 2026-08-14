package com.workflow.entity.version.api.request;

import lombok.Data;

/** 手工固化请求；快照内容始终由服务端读取。 */
@Data
public class ManualVersionCaptureRequest {

    private String triggerCode;
    private String businessIntentCode = "MANUAL_CHECKPOINT";
    private String businessIntentName = "手工固化";
    private String reason;
}
