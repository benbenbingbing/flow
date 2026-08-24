package com.workflow.entity.ui.api.response;

import lombok.Data;

import java.time.LocalDateTime;

/** HOTFIX 观察窗口中的单项运行指标。 */
@Data
public class UiHotfixObservationMetricDTO {

    private String metricCode;
    private Long totalCount;
    private Long failureCount;
    private String lastError;
    private LocalDateTime lastObservedAt;
}
