package com.workflow.process.assignment.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 空办理人事件处置请求。 */
@Data
public class AssigneeIncidentHandleRequest {
    @NotBlank
    @Size(max = 128)
    private String requestId;
    @NotBlank
    private String action;
    private String userId;
    private String groupCode;
    @NotBlank
    @Size(min = 5, max = 500)
    private String reason;
}
