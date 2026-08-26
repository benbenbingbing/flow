package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/**
 * 删除等无资源正文操作的宿主并发结果。
 */
@Value
@Builder
public class UiViewCompositionMutationResultDTO {

    Integer ownerRevision;
}
