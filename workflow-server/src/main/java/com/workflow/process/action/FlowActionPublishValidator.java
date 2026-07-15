package com.workflow.process.action;

import com.workflow.entity.FlowAction;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程动作发布校验器。
 *
 * <p>发布流程前校验所有启用的 flow_action 配置是否合法。</p>
 */
@Component
@RequiredArgsConstructor
public class FlowActionPublishValidator {

    private final FlowActionService flowActionService;
    private final FlowActionConfigurationValidator configurationValidator;

    /**
     * 校验指定流程配置下的草稿动作。
     *
     * @param processConfigId 流程配置 ID
     */
    public void validate(String processConfigId) {
        List<FlowAction> actions = flowActionService.findDraftActions(processConfigId);
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (FlowAction action : actions) {
            if (!Boolean.TRUE.equals(action.getEnabled())) {
                continue;
            }
            configurationValidator.validate(action);
        }
    }
}
