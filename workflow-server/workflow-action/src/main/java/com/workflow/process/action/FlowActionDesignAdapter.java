package com.workflow.process.action;

import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 流程动作设计端口适配器。
 *
 * <p>实现 {@link FlowActionDesignPort} 端口，将流程发布流程的校验、BPMN 清理与动作发布动作
 * 委派给内部组件，实现发布流程与动作模块的解耦。</p>
 */
@Component
@RequiredArgsConstructor
public class FlowActionDesignAdapter implements FlowActionDesignPort {

    private final FlowActionPublishValidator publishValidator;
    private final ProcessFlowActionBpmnInjector bpmnInjector;
    private final FlowActionService flowActionService;

    /**
     * 发布前校验流程配置下所有启用的草稿动作。
     *
     * @param processConfigId 流程配置 ID
     */
    @Override
    public void validateForPublish(String processConfigId) {
        publishValidator.validate(processConfigId);
    }

    /**
     * 清理 BPMN XML 中平台历史注入的顺序流监听器。
     *
     * @param processConfigId 流程配置 ID
     * @param bpmnXml         原始 BPMN XML
     * @return 清理后的 BPMN XML
     */
    @Override
    public String prepareBpmnForPublish(String processConfigId, String bpmnXml) {
        return bpmnInjector.inject(processConfigId, bpmnXml);
    }

    /**
     * 将草稿动作复制发布到指定版本。
     *
     * @param processConfigId 流程配置 ID
     * @param versionId       目标发布版本 ID
     */
    @Override
    public void publishActions(String processConfigId, String versionId) {
        flowActionService.publishActions(processConfigId, versionId);
    }

    /**
     * 逻辑删除指定版本下的全部已发布动作。
     *
     * @param versionId 流程发布版本 ID
     */
    @Override
    public void deleteActionsByVersionId(String versionId) {
        flowActionService.deleteActionsByVersionId(versionId);
    }
}
