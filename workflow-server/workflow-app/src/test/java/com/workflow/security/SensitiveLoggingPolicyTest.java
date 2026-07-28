package com.workflow.security;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SensitiveLoggingPolicyTest {

    @Test
    void businessPayloadsAreNotWrittenToApplicationLogs()
            throws Exception {
        String entityService = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/"
                        + "entity/data/application/"
                        + "EntityDataDynamicService.java"));
        String notificationHandler = Files.readString(Path.of(
                "../workflow-integration/workflow-notification/"
                        + "src/main/java/com/workflow/notification/"
                        + "SendNotificationHandler.java"));

        assertFalse(entityService.contains("data={}"));
        assertFalse(notificationHandler.contains("receiver={}"));
        assertFalse(notificationHandler.contains("startUserId={}"));
    }
}
