package com.workflow.process.status.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.task.application.operation.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProcessCancellationRequirementsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final NodeOperationConfigReader reader = new NodeOperationConfigReader(json);
    private final ProcessCancellationRequirements requirements = new ProcessCancellationRequirements(reader,
            new NodeOperationPolicyParser(json, new NodeOperationConditionEvaluator(), reader));

    @Test void defaultNodesRequireBothTargetsAndDisabledNodesDoNot() {
        assertEquals(Set.of("TERMINATED", "WITHDRAWN"), requirements.requiredCategories(xml("")));
        assertEquals(Set.of(), requirements.requiredCategories(xml("<flowable:property name=\"assigneeConfig\" value='{&quot;allowTerminate&quot;:false}'/>")));
    }

    @Test void legacyMatrixCanEnableOnlyWithdrawal() {
        assertEquals(Set.of("WITHDRAWN"), requirements.requiredCategories(xml(
                "<flowable:property name=\"nodeOperationPolicy\" value='{&quot;version&quot;:1,&quot;operations&quot;:{&quot;withdraw&quot;:{&quot;enabled&quot;:true}}}'/>")));
    }

    @Test void explicitSwitchesRequireIndependentTargets() {
        assertEquals(Set.of("WITHDRAWN"), requirements.requiredCategories(xml(
                "<flowable:property name=\"assigneeConfig\" value='{&quot;allowTerminate&quot;:false,&quot;allowWithdraw&quot;:true}'/>")));
        assertEquals(Set.of("TERMINATED"), requirements.requiredCategories(xml(
                "<flowable:property name=\"assigneeConfig\" value='{&quot;allowTransfer&quot;:false,&quot;allowAddSign&quot;:false,&quot;allowTerminate&quot;:true,&quot;allowWithdraw&quot;:false}'/>")));
    }

    private String xml(String property) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"test\">"
                + "<process id=\"test\"><userTask id=\"u\"><extensionElements><flowable:properties>" + property
                + "</flowable:properties></extensionElements></userTask></process></definitions>";
    }
}
