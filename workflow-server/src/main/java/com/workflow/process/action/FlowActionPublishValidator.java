package com.workflow.process.action;

import com.workflow.entity.FlowAction;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private final ApplicationContext applicationContext;

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
            validateAction(action);
        }
    }

    private void validateAction(FlowAction action) {
        String beanName = action.getInterfaceName();
        if (!StringUtils.hasText(beanName)) {
            throw new RuntimeException("流程动作 '" + action.getActionName() + "' 未配置接口名称（Bean 名称）");
        }

        Object bean;
        try {
            bean = applicationContext.getBean(beanName);
        } catch (Exception e) {
            throw new RuntimeException("流程动作 '" + action.getActionName() + "' 对应的 Bean '" + beanName + "' 不存在，请确认已实现 FlowActionHandler 并注册为 Spring Bean", e);
        }

        if (!(bean instanceof FlowActionHandler)) {
            throw new RuntimeException("流程动作 '" + action.getActionName() + "' 对应的 Bean '" + beanName + "' 未实现 FlowActionHandler 接口");
        }
    }
}
