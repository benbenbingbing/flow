package com.workflow.entity.ui.api.request;

import lombok.Data;

/** HOTFIX 独立复核请求。 */
@Data
public class UiHotfixReviewRequest {

    private Boolean approved;
    private String comment;
}
