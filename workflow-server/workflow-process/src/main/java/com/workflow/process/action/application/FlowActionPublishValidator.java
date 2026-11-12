package com.workflow.process.action.application;

import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.application.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 流程动作发布校验器。
 *
 * <p>发布流程前校验所有启用的 process_action 配置是否合法。</p>
 */
@Component
@RequiredArgsConstructor
public class FlowActionPublishValidator {

    private final FlowActionService flowActionService;
    private final FlowActionConfigurationValidator configurationValidator;

    private final EntityFieldAssignmentFlowActionHandler fieldAssignment;
    private final com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper processConfigs;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
        validateParallelAssignments(processConfigId, actions);
    }

    /** 与已有状态并行校验共用拓扑边界，只比较启用的内置字段赋值动作。 */
    private void validateParallelAssignments(String processId, List<FlowAction> actions) {
        var assignments = actions.stream().filter(action -> Boolean.TRUE.equals(action.getEnabled()))
                .filter(action -> EntityFieldAssignmentFlowActionHandler.BEAN_NAME.equals(action.getInterfaceName())).toList();
        if (assignments.isEmpty()) return;
        Map<String, Map<String, Set<String>>> writes = new HashMap<>();
        for (FlowAction action : assignments) {
            Map<String, Set<String>> flow = writes.computeIfAbsent(action.getElementId(), ignored -> new HashMap<>());
            fieldAssignment.validateConfiguration(action).forEach((code, value) -> {
                // 数值按实际值比较，1.0 与 1.00 等价；JSON 编码区分字符串 "null" 和清空。
                Object normalized = value instanceof java.math.BigDecimal number ? number.stripTrailingZeros() : value;
                flow.computeIfAbsent(code, ignored -> new HashSet<>()).add(objectMapper.valueToTree(normalized).toString());
            });
        }
        var config = processConfigs.selectById(processId);
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(config.getBpmnXml())));
            com.workflow.process.definition.application.EntityTransitionStateValidator.validateFieldAssignments(document, writes);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("无法校验并行分支字段赋值", error);
        }
    }
}
