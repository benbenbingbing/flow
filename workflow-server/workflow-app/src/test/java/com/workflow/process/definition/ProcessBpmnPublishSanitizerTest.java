package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.ServiceTask;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程 BPMN 发布清洗器单元测试。
 *
 * <p>被测对象为 {@link ProcessBpmnPublishSanitizer}，验证发布时将 Camunda 命名空间
 * 转换为 Flowable、将草稿流程 Key 替换为运行时 Key、将配置化节点转换为可执行运行时节点，
 * 以及对不完整配置节点的拒绝逻辑。</p>
 */
class ProcessBpmnPublishSanitizerTest {

    /**
     * 清洗时应将 Camunda 属性转换为 Flowable 属性并使用运行时流程 Key。
     *
     * <p>场景：输入含 camunda:assignee 的草稿 BPMN，断言输出含 flowable:assignee、
     * 流程 ID 替换为 expense_flow，且不再包含 camunda: 命名空间。</p>
     */
    @Test
    void sanitizeConvertsCamundaAttributesAndUsesProcessKey() {
        ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String input = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
                  <bpmn:process id="draft_process">
                    <bpmn:userTask id="task-1" name="审批" camunda:assignee="admin" />
                  </bpmn:process>
                  <bpmndi:BPMNPlane id="plane-1" bpmnElement="draft_process" />
                </bpmn:definitions>
                """;

        String result = sanitizer.sanitize(input, "expense_flow");

        assertTrue(result.contains("<bpmn:process id=\"expense_flow\""));
        assertTrue(result.contains("flowable:assignee=\"admin\""));
        assertTrue(result.contains("xmlns:flowable=\"http://flowable.org/bpmn\""));
        assertTrue(result.contains("bpmnElement=\"expense_flow\""));
        assertFalse(result.contains("camunda:"));
    }

    /**
     * 清洗时应将配置化节点转换为可执行运行时 BPMN 节点。
     *
     * <p>场景：REST 服务任务、发送任务、业务规则任务、调用子流程均携带配置 JSON，
     * 断言输出中 send/rule 被转为 serviceTask，callActivity 含输入输出参数与 businessKey，
     * 且解析后的 BpmnModel 类型正确。</p>
     */
    @Test
    void sanitizeTurnsConfiguredNodesIntoExecutableRuntimeBpmn() {
        ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String input = wrap("""
                <bpmn:startEvent id="start" />
                <bpmn:serviceTask id="rest">
                  %s
                </bpmn:serviceTask>
                <bpmn:sendTask id="send">
                  %s
                </bpmn:sendTask>
                <bpmn:businessRuleTask id="rule">
                  %s
                </bpmn:businessRuleTask>
                <bpmn:callActivity id="call">
                  %s
                </bpmn:callActivity>
                <bpmn:endEvent id="end" />
                """.formatted(
                properties(
                        property("restConfig", """
                                {"method":"POST","url":"http://localhost:8080/api/demo/hello","contentType":"application/json"}
                                """),
                        property("serviceResultVariable", "restResult")),
                properties(property("sendConfig", """
                        {"channels":["message"],"to":"admin","subject":"测试","content":"内容","templateKey":"PROCESS_SUBMIT"}
                        """)),
                properties(property("ruleConfig", """
                        {"decisionRef":"approvalDecision","inputVariables":"{\\"amount\\":\\"${amount}\\"}","resultVariable":"decisionResult","mapDecisionResult":true}
                        """)),
                properties(property("callConfig", """
                        {"calledElement":"child_process","callActivityType":"bpmn","inputParameters":"{\\"childAmount\\":\\"${amount}\\"}","outputParameters":"{\\"result\\":\\"${childResult}\\"}","businessKey":"${businessKey}"}
                        """))));

        String result = sanitizer.sanitize(input, "runtime_process");

        assertTrue(result.contains("id=\"rest\" flowable:delegateExpression=\"${restServiceTaskDelegate}\""));
        assertTrue(result.contains("flowable:resultVariableName=\"restResult\""));
        assertTrue(result.contains("<bpmn:serviceTask id=\"send\""));
        assertTrue(result.contains("flowable:delegateExpression=\"${configuredSendTaskDelegate}\""));
        assertFalse(result.contains("<bpmn:sendTask"));
        assertTrue(result.contains("<bpmn:serviceTask id=\"rule\""));
        assertTrue(result.contains("flowable:delegateExpression=\"${configuredDmnTaskDelegate}\""));
        assertFalse(result.contains("<bpmn:businessRuleTask"));
        assertTrue(result.contains("calledElement=\"child_process\""));
        assertTrue(result.contains("flowable:businessKey=\"${businessKey}\""));
        assertTrue(result.contains("<flowable:in sourceExpression=\"${amount}\" target=\"childAmount\" />"));
        assertTrue(result.contains("<flowable:out sourceExpression=\"${childResult}\" target=\"result\" />"));

        BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(
                () -> new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)),
                true,
                false);
        assertTrue(model.getFlowElement("send") instanceof ServiceTask);
        assertTrue(model.getFlowElement("rule") instanceof ServiceTask);
        assertTrue(model.getFlowElement("call") instanceof CallActivity);
        assertEquals("child_process", ((CallActivity) model.getFlowElement("call")).getCalledElement());
    }

    /**
     * 清洗时应在部署前拒绝配置不完整的节点。
     *
     * <p>场景：发送任务缺少接收人、调用子流程缺少 calledElement，
     * 断言分别抛出 IllegalArgumentException 且消息含"接收人"和"子流程Key"。</p>
     */
    @Test
    void sanitizeRejectsIncompleteConfiguredNodesBeforeDeployment() {
        ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());

        IllegalArgumentException sendError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:sendTask id=\"send\">"
                                + properties(property("sendConfig", "{\"channels\":[\"message\"],\"to\":\"\"}"))
                                + "</bpmn:sendTask>"),
                        "runtime_process"));
        assertTrue(sendError.getMessage().contains("接收人"));

        IllegalArgumentException callError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:callActivity id=\"call\">"
                                + properties(property("callConfig", "{\"calledElement\":\"\"}"))
                                + "</bpmn:callActivity>"),
                        "runtime_process"));
        assertTrue(callError.getMessage().contains("子流程Key"));
    }

    /** 将流程元素片段包装为完整的 BPMN definitions 文档 */
    private static String wrap(String elements) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    targetNamespace="http://workflow.test/process">
                  <bpmn:process id="draft_process" isExecutable="true">
                    %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(elements);
    }

    /** 拼接多个 flowable:property 元素并包裹在 extensionElements 中 */
    private static String properties(String... values) {
        return "<bpmn:extensionElements><flowable:properties>"
                + String.join("", values)
                + "</flowable:properties></bpmn:extensionElements>";
    }

    /** 构造单个 flowable:property 元素字符串 */
    private static String property(String name, String value) {
        return "<flowable:property name=\"" + name + "\" value=\"" + escape(value.trim()) + "\" />";
    }

    /** 对 XML 属性值进行实体转义，避免破坏 BPMn 文档结构 */
    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
