package com.workflow.process.definition;

import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BoundaryEvent;
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
                <bpmn:scriptTask id="script">
                  %s
                </bpmn:scriptTask>
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
                        """)),
                properties(property("scriptConfig", """
                        {"scriptFormat":"groovy","script":"def total = 1 + 2\\ntotal","resultVariable":"total","autoStoreVariables":false}
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
        assertTrue(result.contains("<bpmn:serviceTask id=\"script\""));
        assertTrue(result.contains(
                "flowable:delegateExpression=\"${configuredScriptTaskDelegate}\""));
        assertFalse(result.contains("<bpmn:scriptTask"));

        BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(
                () -> new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)),
                true,
                false);
        assertTrue(model.getFlowElement("send") instanceof ServiceTask);
        assertTrue(model.getFlowElement("rule") instanceof ServiceTask);
        assertTrue(model.getFlowElement("call") instanceof CallActivity);
        assertTrue(model.getFlowElement("script") instanceof ServiceTask);
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

        IllegalArgumentException unsupportedChannelError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:sendTask id=\"send\">"
                                + properties(property(
                                        "sendConfig",
                                        "{\"channels\":[\"email\"],"
                                                + "\"to\":\"admin\"}"))
                                + "</bpmn:sendTask>"),
                        "runtime_process"));
        assertTrue(unsupportedChannelError.getMessage().contains("仅支持站内信"));

        IllegalArgumentException callError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:callActivity id=\"call\">"
                                + properties(property("callConfig", "{\"calledElement\":\"\"}"))
                                + "</bpmn:callActivity>"),
                        "runtime_process"));
        assertTrue(callError.getMessage().contains("子流程Key"));

        IllegalArgumentException restError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:serviceTask id=\"rest\">"
                                + properties(property("restConfig", "{\"method\":\"GET\",\"url\":\"\"}"))
                                + "</bpmn:serviceTask>"),
                        "runtime_process"));
        assertTrue(restError.getMessage().contains("请求URL"));

        IllegalArgumentException scriptError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:scriptTask id=\"script\">"
                                + properties(property(
                                        "scriptConfig",
                                        "{\"scriptFormat\":\"javascript\",\"script\":\"\"}"))
                                + "</bpmn:scriptTask>"),
                        "runtime_process"));
        assertTrue(scriptError.getMessage().contains("脚本内容"));

        IllegalArgumentException unsupportedScriptError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:scriptTask id=\"script\">"
                                + properties(property(
                                        "scriptConfig",
                                        "{\"scriptFormat\":\"javascript\","
                                                + "\"script\":\"1 + 1\"}"))
                                + "</bpmn:scriptTask>"),
                        "runtime_process"));
        assertTrue(unsupportedScriptError.getMessage().contains("仅支持 Groovy"));
    }

    /**
     * 显式知会不能覆盖服务或发送任务的主实现，纯知会节点则直接使用知会代理。
     */
    @Test
    void sanitizeAddsCcListenerWithoutReplacingPrimaryTaskImplementation() {
        ProcessBpmnPublishSanitizer sanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String ccConfig = """
                {"enabled":true,"timings":["EXPLICIT"],"channels":["IN_APP"],
                 "recipientRules":[{"type":"USER","values":["admin"]}]}
                """;
        String input = wrap("""
                <bpmn:serviceTask id="rest">
                  %s
                </bpmn:serviceTask>
                <bpmn:sendTask id="send">
                  %s
                </bpmn:sendTask>
                <bpmn:serviceTask id="cc-only">
                  %s
                </bpmn:serviceTask>
                """.formatted(
                properties(
                        property("restConfig", "{\"method\":\"GET\",\"url\":\"http://localhost/test\"}"),
                        property("ccConfig", ccConfig)),
                properties(
                        property("sendConfig", "{\"channels\":[\"message\"],\"to\":\"admin\"}"),
                        property("ccConfig", ccConfig)),
                properties(property("ccConfig", ccConfig))));

        String result = sanitizer.sanitize(input, "runtime_process");

        assertTrue(result.contains(
                "id=\"rest\" flowable:delegateExpression=\"${restServiceTaskDelegate}\""));
        assertTrue(result.contains(
                "id=\"send\" flowable:delegateExpression=\"${configuredSendTaskDelegate}\""));
        assertEquals(
                2,
                occurrences(
                        result,
                        "delegateExpression=\"${ccNotificationDelegate}\" />"));
        assertTrue(result.contains(
                "id=\"cc-only\" flowable:delegateExpression=\"${ccNotificationDelegate}\""));
    }

    /**
     * 接收任务超时应转换为可部署的定时边界事件，并复用原出线路径。
     */
    @Test
    void sanitizeAddsExecutableReceiveTaskTimeoutAndIsIdempotent() {
        ProcessBpmnPublishSanitizer sanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String input = wrap("""
                <bpmn:startEvent id="start" />
                <bpmn:receiveTask id="receive">
                  %s
                </bpmn:receiveTask>
                <bpmn:endEvent id="end" />
                <bpmn:sequenceFlow id="flow-out" sourceRef="receive" targetRef="end">
                  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${approved == 'approve'}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                """.formatted(properties(property(
                "receiveConfig",
                """
                        {"messageRef":"paymentCallback","hasTimeout":true,
                         "timeout":30,"timeoutUnit":"MINUTE","timeoutAction":"continue"}
                        """))));

        String result = sanitizer.sanitize(input, "runtime_process");
        String secondPass = sanitizer.sanitize(result, "runtime_process");

        assertTrue(result.contains("id=\"receive__receive_timeout\""));
        assertTrue(result.contains("attachedToRef=\"receive\""));
        assertTrue(result.contains("<bpmn:timeDuration>PT30M</bpmn:timeDuration>"));
        assertTrue(result.contains(
                "flowable:delegateExpression=\"${receiveTaskTimeoutDelegate}\""));
        assertTrue(result.contains(
                "id=\"receive__receive_timeout_flow__flow-out\""));
        assertTrue(result.contains("sourceRef=\"receive__receive_timeout_handler\""));
        assertTrue(result.contains("targetRef=\"end\""));
        assertEquals(1, occurrences(secondPass, "id=\"receive__receive_timeout\""));
        assertEquals(
                1,
                occurrences(
                        secondPass,
                        "id=\"receive__receive_timeout_handler\""));

        BpmnModel model = parse(result);
        assertTrue(model.getFlowElement(
                "receive__receive_timeout") instanceof BoundaryEvent);
        assertTrue(model.getFlowElement(
                "receive__receive_timeout_handler") instanceof ServiceTask);
    }

    /**
     * 接收任务超时参数不完整时应在部署前拒绝。
     */
    @Test
    void sanitizeRejectsInvalidReceiveTaskTimeout() {
        ProcessBpmnPublishSanitizer sanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());

        IllegalArgumentException timeoutError = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap("<bpmn:receiveTask id=\"receive\">"
                                + properties(property(
                                        "receiveConfig",
                                        "{\"hasTimeout\":true,\"timeout\":0,"
                                                + "\"timeoutUnit\":\"MINUTE\","
                                                + "\"timeoutAction\":\"continue\"}"))
                                + "</bpmn:receiveTask>"),
                        "runtime_process"));

        assertTrue(timeoutError.getMessage().contains("大于0"));
    }

    /** 将流程元素片段包装为完整的 BPMN definitions 文档 */
    private static String wrap(String elements) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    targetNamespace="http://workflow.test/process">
                  <bpmn:process id="draft_process" isExecutable="true">
                    %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(elements);
    }

    /** 将 XML 解析为 Flowable BPMN 模型 */
    private static BpmnModel parse(String xml) {
        return new BpmnXMLConverter().convertToBpmnModel(
                () -> new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                true,
                false);
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

    /** 统计指定文本出现次数 */
    private static int occurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
