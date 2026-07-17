package com.workflow.process.action;

import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlowActionDesignAdapter implements FlowActionDesignPort {

    private final FlowActionPublishValidator publishValidator;
    private final ProcessFlowActionBpmnInjector bpmnInjector;
    private final FlowActionService flowActionService;

    @Override
    public void validateForPublish(String processConfigId) {
        publishValidator.validate(processConfigId);
    }

    @Override
    public String prepareBpmnForPublish(String processConfigId, String bpmnXml) {
        return bpmnInjector.inject(processConfigId, bpmnXml);
    }

    @Override
    public void publishActions(String processConfigId, String versionId) {
        flowActionService.publishActions(processConfigId, versionId);
    }

    @Override
    public void deleteActionsByVersionId(String versionId) {
        flowActionService.deleteActionsByVersionId(versionId);
    }
}
