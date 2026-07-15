package com.workflow.process.action;

import com.workflow.dto.FlowActionTimingOptionDTO;

import java.util.Collection;

public interface FlowActionTriggerProvider {

    Collection<FlowActionTimingOptionDTO> getTriggerOptions();
}
