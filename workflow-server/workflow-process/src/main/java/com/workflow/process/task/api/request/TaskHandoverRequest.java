package com.workflow.process.task.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 管理员交接请求；all 作用于来源人员的全部未完成任务，不受页面分页影响。 */
public record TaskHandoverRequest(
        @NotBlank @Size(max = 64) String sourceUserId,
        @NotBlank @Size(max = 64) String targetUserId,
        List<@NotBlank @Size(max = 64) String> taskIds,
        boolean all,
        @NotBlank @Size(max = 500) String reason) {
}
