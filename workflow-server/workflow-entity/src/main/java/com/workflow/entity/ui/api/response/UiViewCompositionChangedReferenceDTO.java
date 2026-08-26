package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/** LINK/UNLINK 完成后返回的受控关系变更摘要。 */
@Value
@Builder
public class UiViewCompositionChangedReferenceDTO {

    String sourceRecordId;
    String targetRecordId;
    String relationType;
    boolean linked;
    boolean changed;
    boolean replayed;
}
