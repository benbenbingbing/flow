package com.workflow.entity.ui.api.request;

import lombok.Data;

/** 运行时上报 HOTFIX 观察指标。 */
@Data
public class UiHotfixObservationMetricRequest {

    private String metricCode;
    private Boolean successful;
    private String errorMessage;
}
