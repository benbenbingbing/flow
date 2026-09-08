package com.workflow.process.definition.application;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 流程节点同步执行归一化测试。 */
class ProcessBpmnSynchronousExecutionNormalizerTest {

    @Test
    void removesEngineAsyncAttributesAndPreservesOtherProperties() throws Exception {
        String input = """
                <bpmn:definitions
                    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                    xmlns:activiti="http://activiti.org/bpmn"
                    xmlns:custom="urn:workflow:test">
                  <bpmn:process id="draft" flowable:async="true"
                      flowable:noWaitStatesAsyncLeave="true" flowable:jobPriority="50">
                    <bpmn:userTask id="review" name="审批"
                        asyncAfter="true"
                        flowable:async="true" flowable:asyncBefore="true"
                        flowable:asyncAfter="true" flowable:asyncLeave="true"
                        flowable:exclusive="false" flowable:asyncLeaveExclusive="false"
                        flowable:assignee="admin"
                        camunda:asyncBefore="true" camunda:asyncAfter="true"
                        camunda:exclusive="false" camunda:jobPriority="10"
                        activiti:async="true" activiti:asyncLeave="true"
                        activiti:exclusive="false"
                        custom:async="keep" custom:exclusive="keep" />
                    <bpmn:intermediateThrowEvent id="signal">
                      <bpmn:signalEventDefinition flowable:async="true"
                          custom:async="keep-signal" />
                    </bpmn:intermediateThrowEvent>
                    <custom:userTask async="keep-unqualified" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        String result =
                ProcessBpmnSynchronousExecutionNormalizer.normalize(input);
        Document document = parse(result);
        Element process = element(document, "process");
        Element task = element(document, "userTask");
        Element signal = element(document, "signalEventDefinition");
        Element customTask = (Element) document.getElementsByTagNameNS(
                "urn:workflow:test", "userTask").item(0);

        assertFalse(process.hasAttributeNS(
                "http://flowable.org/bpmn", "async"));
        assertFalse(process.hasAttributeNS(
                "http://flowable.org/bpmn", "noWaitStatesAsyncLeave"));
        assertEquals("50", process.getAttributeNS(
                "http://flowable.org/bpmn", "jobPriority"));

        assertFalse(task.hasAttribute("asyncAfter"));
        assertFalse(task.hasAttributeNS(
                "http://flowable.org/bpmn", "async"));
        assertFalse(task.hasAttributeNS(
                "http://flowable.org/bpmn", "asyncBefore"));
        assertFalse(task.hasAttributeNS(
                "http://flowable.org/bpmn", "asyncAfter"));
        assertFalse(task.hasAttributeNS(
                "http://flowable.org/bpmn", "asyncLeave"));
        assertFalse(task.hasAttributeNS(
                "http://flowable.org/bpmn", "exclusive"));
        assertFalse(task.hasAttributeNS(
                "http://flowable.org/bpmn", "asyncLeaveExclusive"));
        assertFalse(task.hasAttributeNS(
                "http://camunda.org/schema/1.0/bpmn", "asyncBefore"));
        assertFalse(task.hasAttributeNS(
                "http://camunda.org/schema/1.0/bpmn", "asyncAfter"));
        assertFalse(task.hasAttributeNS(
                "http://camunda.org/schema/1.0/bpmn", "exclusive"));
        assertFalse(task.hasAttributeNS(
                "http://activiti.org/bpmn", "async"));
        assertFalse(task.hasAttributeNS(
                "http://activiti.org/bpmn", "asyncLeave"));
        assertFalse(task.hasAttributeNS(
                "http://activiti.org/bpmn", "exclusive"));

        assertEquals("admin", task.getAttributeNS(
                "http://flowable.org/bpmn", "assignee"));
        assertEquals("10", task.getAttributeNS(
                "http://camunda.org/schema/1.0/bpmn", "jobPriority"));
        assertEquals("keep", task.getAttributeNS(
                "urn:workflow:test", "async"));
        assertEquals("keep", task.getAttributeNS(
                "urn:workflow:test", "exclusive"));
        assertFalse(signal.hasAttributeNS(
                "http://flowable.org/bpmn", "async"));
        assertEquals("keep-signal", signal.getAttributeNS(
                "urn:workflow:test", "async"));
        assertEquals("keep-unqualified", customTask.getAttribute("async"));

        assertEquals(
                result,
                ProcessBpmnSynchronousExecutionNormalizer.normalize(result));
    }

    @Test
    void leavesDraftWithoutAsyncCandidatesByteForByteUntouched() {
        String unfinishedDraft =
                "<bpmn:definitions>尚未完成，但没有异步属性</bpmn:definitions>\r\n";

        assertEquals(
                unfinishedDraft,
                ProcessBpmnSynchronousExecutionNormalizer.normalize(
                        unfinishedDraft));
    }

    @Test
    void removesCompleteAsyncFromCallActivitiesAndPreservesCustomElements()
            throws Exception {
        String input = """
                <bpmn:definitions
                    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    xmlns:engine="http://flowable.org/bpmn"
                    xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                    xmlns:activiti="http://activiti.org/bpmn"
                    xmlns:custom="urn:workflow:test">
                  <bpmn:process id="draft">
                    <bpmn:callActivity id="flowable-call"
                        calledElement="child"
                        flowable:completeAsync="true" />
                    <bpmn:callActivity id="engine-prefix-call"
                        calledElement="child"
                        engine:completeAsync="true" />
                    <bpmn:callActivity id="camunda-call"
                        calledElement="child"
                        camunda:completeAsync="true" />
                    <bpmn:callActivity id="activiti-call"
                        calledElement="child"
                        activiti:completeAsync="true" />
                    <bpmn:callActivity id="bare-call"
                        calledElement="child"
                        completeAsync="true" />
                    <custom:callActivity id="custom-call"
                        flowable:completeAsync="keep" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        String result =
                ProcessBpmnSynchronousExecutionNormalizer.normalize(input);
        Document document = parse(result);

        for (String id : new String[] {
                "flowable-call",
                "engine-prefix-call",
                "camunda-call",
                "activiti-call",
                "bare-call"}) {
            Element callActivity = elementById(document, id);
            assertFalse(callActivity.hasAttribute("completeAsync"));
            assertFalse(callActivity.hasAttributeNS(
                    "http://flowable.org/bpmn", "completeAsync"));
            assertFalse(callActivity.hasAttributeNS(
                    "http://camunda.org/schema/1.0/bpmn", "completeAsync"));
            assertFalse(callActivity.hasAttributeNS(
                    "http://activiti.org/bpmn", "completeAsync"));
        }
        assertEquals(
                "keep",
                elementById(document, "custom-call").getAttributeNS(
                        "http://flowable.org/bpmn", "completeAsync"));
    }

    @Test
    void rejectsMalformedXmlWhenItContainsAsyncConfiguration() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ProcessBpmnSynchronousExecutionNormalizer.normalize(
                        "<bpmn:userTask flowable:async=\"true\">"));

        assertTrue(exception.getMessage().startsWith(
                "BPMN_SYNC_NORMALIZATION_INVALID:"));
    }

    @Test
    void completeAsyncParticipatesInTheFastCandidateCheck() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ProcessBpmnSynchronousExecutionNormalizer.normalize(
                        "<bpmn:callActivity completeAsync=\"true\">"));

        assertTrue(exception.getMessage().startsWith(
                "BPMN_SYNC_NORMALIZATION_INVALID:"));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(xml)));
    }

    private static Element element(Document document, String localName) {
        return (Element) document.getElementsByTagNameNS(
                "*", localName).item(0);
    }

    private static Element elementById(Document document, String id) {
        for (int index = 0;
                index < document.getElementsByTagName("*").getLength();
                index++) {
            Element element = (Element) document
                    .getElementsByTagName("*")
                    .item(index);
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing element: " + id);
    }
}
