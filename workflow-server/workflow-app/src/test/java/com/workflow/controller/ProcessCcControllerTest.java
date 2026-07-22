package com.workflow.controller;

import com.workflow.common.ForbiddenException;
import com.workflow.common.UserContext;
import com.workflow.service.CurrentUserRoleService;
import com.workflow.service.ProcessCcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProcessCcControllerTest {
    private final ProcessCcService ccService = mock(ProcessCcService.class);
    private final CurrentUserRoleService roleService = mock(CurrentUserRoleService.class);
    private final ProcessCcController controller = new ProcessCcController(ccService, roleService);

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void processCcRecordsRequireAdministrator() {
        UserContext.setCurrentUser("admin-id", "admin");
        when(ccService.getProcessCcRecords("process-1")).thenReturn(List.of());

        controller.getProcessCcRecords("process-1");

        verify(roleService).requireAdministrator("仅管理员可以查看流程全部知会记录");
        verify(ccService).getProcessCcRecords("process-1");
    }

    @Test
    void processCcRecordsRejectAnonymousUser() {
        assertThrows(ForbiddenException.class, () -> controller.getProcessCcRecords("process-1"));
        verifyNoInteractions(roleService, ccService);
    }
}
