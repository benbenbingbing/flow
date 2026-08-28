package com.workflow.process.assignment.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssigneeIncidentTransactionBoundaryTest {

    @Test
    void failureCreationSurvivesRollbackButRecoveryFollowsNodeEntryTransaction()
            throws Exception {
        Transactional create = AssigneeIncidentRecorder.class
                .getMethod(
                        "create",
                        AssigneeIncidentRecorder.CreateCommand.class)
                .getAnnotation(Transactional.class);
        Transactional recover = AssigneeIncidentRecorder.class
                .getMethod(
                        "resolveOpenNodeEntry",
                        String.class,
                        String.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, create.propagation());
        assertEquals(Propagation.REQUIRED, recover.propagation());
    }
}
