package com.workflow.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class UiConfigDiffItemDTO {

    String section;
    String id;
    String label;
    String changeType;
    List<String> changedFields;
}
