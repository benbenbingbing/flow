package com.workflow.controller;

import com.workflow.common.ForbiddenException;
import com.workflow.service.CurrentUserRoleService;
import com.workflow.service.FlowActionExecutionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlowActionExecutionControllerTest {

    @Test
    void shouldRejectExecutionLogQueryForNonSuperAdmin() {
        FlowActionExecutionService executionService = mock(FlowActionExecutionService.class);
        CurrentUserRoleService roleService = mock(CurrentUserRoleService.class);
        doThrow(new ForbiddenException("forbidden")).when(roleService).requireSuperAdmin();
        FlowActionExecutionController controller = new FlowActionExecutionController(
                executionService,
                roleService);

        assertThrows(
                ForbiddenException.class,
                () -> controller.listByProcessInstance("pi-1"));
        verify(executionService, never()).findDetailsByProcessInstanceId("pi-1");
    }
}
