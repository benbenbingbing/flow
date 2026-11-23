package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.task.application.operation.NodeHistoryReturnPolicy;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Process;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProcessReturnPolicyBpmnTest {
    private static final String XML = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
              <process id="flow" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="apply" name="申请"/>
                <userTask id="review"> %s </userTask>
                <userTask id="finalize"/>
                <sequenceFlow id="first" sourceRef="start" targetRef="apply"/>
                <sequenceFlow id="second" sourceRef="apply" targetRef="review"/>
                <sequenceFlow id="third" sourceRef="review" targetRef="finalize"/>
              </process>
            </definitions>
            """;
    private final NodeHistoryReturnPolicy policy = new NodeHistoryReturnPolicy(new ObjectMapper());

    @Test
    void defaultCarriesAcrossBlankCreationAndReplacementButLegacyDoesNotInheritIt() throws Exception {
        String blank = ProcessReturnPolicyBpmn.withDefault(null, true);
        String design = XML.formatted("");
        assertTrue(ProcessReturnPolicyBpmn.hasAutomaticDefault(blank));
        String newDraft = ProcessReturnPolicyBpmn.preserveDefault(blank, design);
        assertTrue(ProcessReturnPolicyBpmn.hasAutomaticDefault(newDraft));
        Process current = publish(newDraft);
        assertEquals(Set.of("apply", "review"), config(current, "finalize").targetNodeIds());
        assertEquals(NodeHistoryReturnPolicy.Mode.OFF, config(current, "apply").mode());

        String updatedLegacy = ProcessReturnPolicyBpmn.preserveDefault(design, newDraft);
        assertFalse(ProcessReturnPolicyBpmn.hasAutomaticDefault(updatedLegacy));
        assertEquals(NodeHistoryReturnPolicy.Mode.OFF, config(publish(updatedLegacy), "finalize").mode());
    }

    @Test
    void changedSettingsAreAuthoritativeAndOnlyRelevantChangesReplaceSnapshot() throws Exception {
        String off = """
                <extensionElements><flowable:properties>
                  <flowable:property name="taskReturnConfig" value='{"mode":"OFF"}'/>
                </flowable:properties></extensionElements>
                """;
        String design = ProcessReturnPolicyBpmn.withDefault(XML.formatted(off), true);
        String snapshot = policy.compile(design, "flow", null);
        // 旧快照带上空白，断言重新发布原样复用，而非重新计算相同的 JSON。
        String existing = " " + snapshot;
        Process previous = model(ProcessReturnPolicyBpmn.withPublishedSnapshot(design, "flow", existing));
        assertEquals(existing, policy.compile(design.replace("申请", "填写申请"), "flow", previous));
        assertEquals(NodeHistoryReturnPolicy.Mode.OFF, config(previous, "review").mode());
        assertEquals(Set.of("apply", "review"), config(previous, "finalize").targetNodeIds());

        String fixed = design.replace("&quot;OFF&quot;",
                "&quot;FIXED&quot;,&quot;targetNodeIds&quot;:[&quot;apply&quot;]");
        assertNotEquals(existing, policy.compile(fixed, "flow", previous));
        assertEquals(Set.of("apply"), config(publish(fixed), "review").targetNodeIds());
        String moved = design.replace("sourceRef=\"review\" targetRef=\"finalize\"",
                "sourceRef=\"start\" targetRef=\"finalize\"");
        assertNotEquals(existing, policy.compile(moved, "flow", previous));
        assertEquals(NodeHistoryReturnPolicy.Mode.OFF, config(publish(moved), "finalize").mode());
        assertThrows(IllegalArgumentException.class, () -> policy.compile(fixed.replace(
                "sourceRef=\"apply\" targetRef=\"review\"", "sourceRef=\"start\" targetRef=\"review\""), "flow", previous));
    }

    private Process publish(String design) throws Exception {
        return model(ProcessReturnPolicyBpmn.withPublishedSnapshot(design, "flow", policy.compile(design, "flow", null)));
    }

    private NodeHistoryReturnPolicy.Configuration config(Process process, String id) {
        return policy.readPublished(process, process.getFlowElement(id));
    }

    private Process model(String xml) throws Exception {
        var reader = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
        try {
            return new BpmnXMLConverter().convertToBpmnModel(reader).getMainProcess();
        } finally {
            reader.close();
        }
    }
}
