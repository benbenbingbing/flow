package com.workflow.process.action.application;

import com.workflow.process.action.infrastructure.flowable.ProcessFlowActionBpmnInjector;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionVersionCount;

import com.workflow.process.definition.application.port.FlowActionDesignPort;
import com.workflow.process.action.application.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程动作设计端口适配器。
 *
 * <p>实现 {@link FlowActionDesignPort} 端口，将流程版本管理的校验、BPMN 清理、动作发布与统计
 * 委派给内部组件，实现流程定义与动作模块的解耦。</p>
 */
@Component
@RequiredArgsConstructor
public class FlowActionDesignAdapter implements FlowActionDesignPort {

    private final FlowActionPublishValidator publishValidator;
    private final ProcessFlowActionBpmnInjector bpmnInjector;
    private final FlowActionService flowActionService;
    private final FlowActionMapper flowActionMapper;

    /**
     * 一次聚合查询返回所有目标版本的动作数量，避免版本历史列表逐行查询动作。
     *
     * @param versionIds 发布版本 ID 列表
     * @return 各版本未删除的已发布动作数量；不按 enabled 过滤，以匹配明细列表
     */
    @Override
    public Map<String, Long> countPublishedActionsByVersionIds(List<String> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return flowActionMapper.countPublishedActionsByVersionIds(versionIds).stream()
                .collect(Collectors.toMap(FlowActionVersionCount::getVersionId,
                        FlowActionVersionCount::getActionCount));
    }

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
