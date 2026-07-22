package com.workflow.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class UiConfigDiffDTO {

    String configType;
    String configId;
    String draftHash;
    String activeHash;
    boolean changed;
    List<String> changedSections;
    List<UiConfigDiffItemDTO> changedItems;
}
