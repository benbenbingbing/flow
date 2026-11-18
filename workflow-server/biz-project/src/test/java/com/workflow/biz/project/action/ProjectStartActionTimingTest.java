package com.workflow.biz.project.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.biz.project.service.ProjectGovernanceService;
import com.workflow.biz.project.service.ProjectMemberChangeService;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 真实随包配置与处理器声明必须同步迁移，否则导入后的流程无法通过发布校验。 */
class ProjectStartActionTimingTest {
    @ParameterizedTest
    @CsvSource({"project_initiation_process,validateProjectInitiationHandler",
            "project_system_change_process,validateProjectSystemChangeHandler",
            "project_member_change_process,validateProjectMemberChangeHandler"})
    void bundledStartActionMatchesTheHandlersNewTiming(String process, String handlerName) throws Exception {
        FlowActionProvider handler = switch (handlerName) {
            case "validateProjectInitiationHandler" -> new ValidateProjectInitiationHandler(mock(ProjectGovernanceService.class));
            case "validateProjectSystemChangeHandler" -> new ValidateProjectSystemChangeHandler(mock(ProjectGovernanceService.class));
            default -> new ValidateProjectMemberChangeHandler(mock(ProjectMemberChangeService.class));
        };
        assertEquals(Set.of("PROCESS_START_EXECUTED"), handler.supportedTriggerTimings());
        try (var input = getClass().getResourceAsStream("/project-config/assets/processes/" + process + "-v1.json")) {
            assertNotNull(input);
            var config = new ObjectMapper().readTree(input);
            assertFalse(config.findValuesAsText("triggerTiming").contains("PROCESS_STARTED"));
            int matched = 0;
            for (var action : config.path("flowActions")) {
                if (!handlerName.equals(action.path("interfaceName").asText())) continue;
                matched++;
                assertTrue(handler.supportedTriggerTimings().contains(action.path("triggerTiming").asText()));
                assertEquals("IN_TRANSACTION", action.path("executionMode").asText());
                assertEquals("ROLLBACK", action.path("failurePolicy").asText());
            }
            assertEquals(1, matched);
        }
    }
}
