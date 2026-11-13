package com.workflow.process.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.instance.api.response.ProcessFormRuntimeDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 新接口的首屏契约不包含历史或图形，避免重构时再次带回大聚合。 */
class ProcessProgressRuntimeServiceFormTest {
    @Test void firstScreenContractDoesNotExposeDiagramOrTimeline() {
        var value = new ObjectMapper().valueToTree(new ProcessFormRuntimeDTO());
        for (String name : java.util.List.of("bpmnXml", "nodeHistory", "rounds", "nodeAssigneeMap", "formConfigs")) {
            assertFalse(value.has(name), name);
        }
        assertTrue(value.has("formConfig"));
        assertTrue(value.has("entityData"));
    }
}
