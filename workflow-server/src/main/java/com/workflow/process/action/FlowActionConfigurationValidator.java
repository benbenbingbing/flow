package com.workflow.process.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.FlowActionTimingOptionDTO;
import com.workflow.entity.FlowAction;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FlowActionConfigurationValidator {

    private final ProcessDefinitionConfigMapper processConfigMapper;
    private final ApplicationContext applicationContext;
    private final FlowActionTimingCatalog timingCatalog;
    private final ObjectMapper objectMapper;

    public void validate(FlowAction action) {
        FlowActionScopeType scopeType = parseScope(action.getScopeType());
        FlowActionExecutionMode executionMode = parseExecutionMode(action.getExecutionMode());
        FlowActionFailurePolicy failurePolicy = parseFailurePolicy(action.getFailurePolicy());
        FlowActionTimingOptionDTO timing = timingCatalog.find(action.getTriggerTiming())
                .orElseThrow(() -> new RuntimeException("不支持的流程动作时机: " + action.getTriggerTiming()));

        if (!scopeType.name().equalsIgnoreCase(timing.getScopeType())) {
            throw new RuntimeException("执行时机 " + timing.getLabel() + " 不适用于 " + scopeType + " 作用域");
        }
        if (scopeType != FlowActionScopeType.PROCESS && !StringUtils.hasText(action.getElementId())) {
            throw new RuntimeException("节点或连线动作必须配置 BPMN 元素 ID");
        }
        validatePolicy(executionMode, failurePolicy);
        validateRetryConfig(action.getRetryConfig());
        validateHandler(action, executionMode);
        validateElement(action, timing);
    }

    private void validateHandler(FlowAction action, FlowActionExecutionMode executionMode) {
        if (!StringUtils.hasText(action.getInterfaceName())) {
            throw new RuntimeException("流程动作未配置处理器");
        }
        Object bean;
        try {
            bean = applicationContext.getBean(action.getInterfaceName());
        } catch (Exception e) {
            throw new RuntimeException("流程动作处理器不存在: " + action.getInterfaceName(), e);
        }
        if (!(bean instanceof FlowActionHandler handler)) {
            throw new RuntimeException("Bean '" + action.getInterfaceName() + "' 未实现 FlowActionHandler 接口");
        }
        Set<String> supportedTimings = handler.supportedTriggerTimings();
        if (supportedTimings != null && !supportedTimings.isEmpty()
                && supportedTimings.stream().noneMatch(action.getTriggerTiming()::equalsIgnoreCase)) {
            throw new RuntimeException("处理器不支持执行时机: " + action.getTriggerTiming());
        }
        Set<String> supportedModes = handler.supportedExecutionModes();
        if (supportedModes != null && !supportedModes.isEmpty()
                && supportedModes.stream().noneMatch(executionMode.name()::equalsIgnoreCase)) {
            throw new RuntimeException("处理器不支持执行方式: " + executionMode);
        }
    }

    private void validateElement(FlowAction action, FlowActionTimingOptionDTO timing) {
        ProcessDefinitionConfig process = processConfigMapper.selectById(action.getProcessConfigId());
        if (process == null || !StringUtils.hasText(process.getBpmnXml())) {
            throw new RuntimeException("流程定义或 BPMN XML 不存在");
        }
        if (FlowActionScopeType.PROCESS.name().equals(action.getScopeType())) {
            return;
        }
        Element element = findElement(process.getBpmnXml(), action.getElementId());
        if (element == null) {
            throw new RuntimeException("BPMN 元素不存在: " + action.getElementId());
        }
        String localName = element.getLocalName();
        if (FlowActionScopeType.SEQUENCE_FLOW.name().equals(action.getScopeType())
                && !"sequenceFlow".equals(localName)) {
            throw new RuntimeException("连线动作只能绑定 sequenceFlow 元素");
        }
        if (FlowActionScopeType.NODE.name().equals(action.getScopeType())
                && "sequenceFlow".equals(localName)) {
            throw new RuntimeException("节点动作不能绑定 sequenceFlow 元素");
        }
        if (Boolean.TRUE.equals(timing.getUserTaskOnly()) && !"userTask".equals(localName)) {
            throw new RuntimeException(timing.getLabel() + " 只能配置在用户任务节点");
        }
    }

    private Element findElement(String bpmnXml, String elementId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            NodeList elements = document.getElementsByTagNameNS("*", "*");
            for (int index = 0; index < elements.getLength(); index++) {
                Element element = (Element) elements.item(index);
                if (elementId.equals(element.getAttribute("id"))) {
                    return element;
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("解析 BPMN XML 失败", e);
        }
    }

    private void validatePolicy(FlowActionExecutionMode executionMode, FlowActionFailurePolicy failurePolicy) {
        if (executionMode == FlowActionExecutionMode.IN_TRANSACTION
                && failurePolicy != FlowActionFailurePolicy.ROLLBACK
                && failurePolicy != FlowActionFailurePolicy.CONTINUE) {
            throw new RuntimeException("事务内动作只支持 ROLLBACK 或 CONTINUE");
        }
        if (executionMode == FlowActionExecutionMode.AFTER_COMMIT
                && failurePolicy != FlowActionFailurePolicy.RETRY
                && failurePolicy != FlowActionFailurePolicy.IGNORE) {
            throw new RuntimeException("提交后动作只支持 RETRY 或 IGNORE");
        }
    }

    private void validateRetryConfig(String retryConfig) {
        if (!StringUtils.hasText(retryConfig)) {
            return;
        }
        try {
            var node = objectMapper.readTree(retryConfig);
            if (!node.isObject()) {
                throw new RuntimeException("重试配置必须是 JSON 对象");
            }
            if (node.has("maxRetries")) {
                int maxRetries = node.get("maxRetries").asInt(-1);
                if (maxRetries < 0 || maxRetries > 20) {
                    throw new RuntimeException("最大重试次数必须在 0 到 20 之间");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("重试配置 JSON 不合法", e);
        }
    }

    private FlowActionScopeType parseScope(String value) {
        try {
            return FlowActionScopeType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new RuntimeException("不支持的动作作用域: " + value);
        }
    }

    private FlowActionExecutionMode parseExecutionMode(String value) {
        try {
            return FlowActionExecutionMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new RuntimeException("不支持的执行方式: " + value);
        }
    }

    private FlowActionFailurePolicy parseFailurePolicy(String value) {
        try {
            return FlowActionFailurePolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new RuntimeException("不支持的失败策略: " + value);
        }
    }
}
