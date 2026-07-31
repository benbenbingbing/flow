package com.workflow.process.sla;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.process.sla.policy.api.request.TaskSlaPolicySaveRequest;
import com.workflow.process.sla.policy.application.TaskSlaPolicyService;
import com.workflow.process.sla.policy.infrastructure.persistence.mapper.TaskSlaEscalationStepMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.mapper.TaskSlaPolicyMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TaskSlaPolicyServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"AUTO_APPROVE", "AUTO_REJECT"})
    void rejectsAutomaticDecisionActions(String actionType) {
        TaskSlaPolicyService service = new TaskSlaPolicyService(
                mock(TaskSlaPolicyMapper.class),
                mock(TaskSlaEscalationStepMapper.class),
                new ObjectMapper(),
                mock(MigrationAssetHandler.class));
        TaskSlaPolicySaveRequest request =
                new TaskSlaPolicySaveRequest(
                        "DEFAULT_SLA",
                        "默认SLA",
                        null,
                        60,
                        480,
                        "WORKING_TIME",
                        "WORKING_TIME",
                        false,
                        true,
                        null,
                        List.of(
                                new TaskSlaPolicySaveRequest
                                        .EscalationStepRequest(
                                        "非法自动决策",
                                        "COMPLETION",
                                        "AT_DUE",
                                        0,
                                        null,
                                        1,
                                        actionType,
                                        null,
                                        "{}",
                                        "{}")));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(null, request));
    }
}
