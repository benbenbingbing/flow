package com.workflow.process.migration.application;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProcessInstanceMigrationServiceBeanNameTest {

    @Test
    void usesPlatformSpecificBeanNameToAvoidFlowableAutoConfigurationCollision() {
        Service annotation = ProcessInstanceMigrationService.class.getAnnotation(Service.class);

        assertNotNull(annotation);
        assertEquals("workflowProcessInstanceMigrationService", annotation.value());
    }
}
