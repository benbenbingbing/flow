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

/**
 * 流程动作配置校验器。
 *
 * <p>在保存或发布流程动作前，对作用域、执行时机、执行方式、失败策略、重试配置、处理器
 * 以及 BPMN 元素绑定进行合法性校验，确保后续执行不会因配置错误而失败。</p>
 */
@Component
@RequiredArgsConstructor
public class FlowActionConfigurationValidator {

    private final ProcessDefinitionConfigMapper processConfigMapper;
    private final ApplicationContext applicationContext;
    private final FlowActionTimingCatalog timingCatalog;
    private final ObjectMapper objectMapper;

    /**
     * 校验单条动作配置的完整合法性。
     *
     * @param action 待校验动作
     * @throws RuntimeException 任一校验项不通过时抛出，描述具体原因
     */
    public void validate(FlowAction action) {
        FlowActionScopeType scopeType = parseScope(action.getScopeType());
        FlowActionExecutionMode executionMode = parseExecutionMode(action.getExecutionMode());
        FlowActionFailurePolicy failurePolicy = parseFailurePolicy(action.getFailurePolicy());
        FlowActionTimingOptionDTO timing = timingCatalog.find(action.getTriggerTiming())
                .orElseThrow(() -> new RuntimeException("不支持的流程动作时机: " + action.getTriggerTiming()));

        // 执行时机与作用域必须匹配，如任务级时机不能用于流程级作用域
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

    /**
     * 校验处理器是否存在、是否实现接口，以及是否声明支持当前触发时机与执行方式。
     *
     * @param action        动作配置
     * @param executionMode 已归一化的执行方式
     */
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
        // 处理器可声明仅支持部分触发时机
        Set<String> supportedTimings = handler.supportedTriggerTimings();
        if (supportedTimings != null && !supportedTimings.isEmpty()
                && supportedTimings.stream().noneMatch(action.getTriggerTiming()::equalsIgnoreCase)) {
            throw new RuntimeException("处理器不支持执行时机: " + action.getTriggerTiming());
        }
        // 处理器可声明仅支持部分执行方式
        Set<String> supportedModes = handler.supportedExecutionModes();
        if (supportedModes != null && !supportedModes.isEmpty()
                && supportedModes.stream().noneMatch(executionMode.name()::equalsIgnoreCase)) {
            throw new RuntimeException("处理器不支持执行方式: " + executionMode);
        }
    }

    /**
     * 校验动作绑定的 BPMN 元素是否存在，且元素类型与作用域、执行时机一致。
     *
     * @param action  动作配置
     * @param timing  执行时机选项
     */
    private void validateElement(FlowAction action, FlowActionTimingOptionDTO timing) {
        ProcessDefinitionConfig process = processConfigMapper.selectById(action.getProcessConfigId());
        if (process == null || !StringUtils.hasText(process.getBpmnXml())) {
            throw new RuntimeException("流程定义或 BPMN XML 不存在");
        }
        // 流程级动作不绑定具体元素，直接放行
        if (FlowActionScopeType.PROCESS.name().equals(action.getScopeType())) {
            return;
        }
        Element element = findElement(process.getBpmnXml(), action.getElementId());
        if (element == null) {
            throw new RuntimeException("BPMN 元素不存在: " + action.getElementId());
        }
        String localName = element.getLocalName();
        // 连线动作只能绑定 sequenceFlow
        if (FlowActionScopeType.SEQUENCE_FLOW.name().equals(action.getScopeType())
                && !"sequenceFlow".equals(localName)) {
            throw new RuntimeException("连线动作只能绑定 sequenceFlow 元素");
        }
        // 节点动作不能绑定 sequenceFlow
        if (FlowActionScopeType.NODE.name().equals(action.getScopeType())
                && "sequenceFlow".equals(localName)) {
            throw new RuntimeException("节点动作不能绑定 sequenceFlow 元素");
        }
        // 仅用户任务适用的时机（如待办创建）必须绑定 userTask
        if (Boolean.TRUE.equals(timing.getUserTaskOnly()) && !"userTask".equals(localName)) {
            throw new RuntimeException(timing.getLabel() + " 只能配置在用户任务节点");
        }
    }

    /**
     * 在 BPMN XML 中按 id 属性查找元素。
     *
     * @param bpmnXml    BPMN XML 文本
     * @param elementId  目标元素 ID
     * @return 命中的元素；未找到返回 null
     * @throws RuntimeException XML 解析失败时抛出
     */
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

    /**
     * 校验执行方式与失败策略的组合是否允许。
     *
     * @param executionMode 执行方式
     * @param failurePolicy 失败策略
     */
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

    /**
     * 校验重试配置 JSON：必须是 JSON 对象，且 maxRetries 在 0~20 之间。
     *
     * @param retryConfig 重试配置 JSON 字符串
     */
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
