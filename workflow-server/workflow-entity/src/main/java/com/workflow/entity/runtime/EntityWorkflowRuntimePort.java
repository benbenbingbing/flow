package com.workflow.entity.runtime;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;

public interface EntityWorkflowRuntimePort {

    void startProcess(EntityDataDTO dto, EntityDefinition definition);

    void updateCurrentTask(String entityCode,
                           String entityDataId,
                           String currentTaskId,
                           String currentTaskName,
                           String currentTaskAssignee);
}
