package com.workflow.service.cc;

import java.util.Map;

public record CcRuntimeContext(
        String processInstanceId,
        String processDefinitionId,
        String processKey,
        String processName,
        String businessKey,
        String nodeId,
        String nodeName,
        String timing,
        String operatorId,
        Map<String, Object> variables) {
}
