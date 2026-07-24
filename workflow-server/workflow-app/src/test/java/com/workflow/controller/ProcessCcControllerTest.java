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

/**
 * 流程知会记录控制器单元测试。
 *
 * <p>被测对象为 {@link ProcessCcController}，验证查看流程知会记录时
 * 必须具备管理员权限，匿名用户应被直接拒绝。</p>
 */
class ProcessCcControllerTest {
    /** 模拟的流程知会服务 */
    private final ProcessCcService ccService = mock(ProcessCcService.class);
    /** 模拟的当前用户角色服务，用于校验管理员权限 */
    private final CurrentUserRoleService roleService = mock(CurrentUserRoleService.class);
    /** 被测控制器实例 */
    private final ProcessCcController controller = new ProcessCcController(ccService, roleService);

    /** 每个测试后清理用户上下文，避免测试间状态泄漏 */
    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    /**
     * 管理员用户查询流程知会记录应通过权限校验并调用服务。
     *
     * <p>场景：当前用户为 admin，断言 roleService.requireAdministrator 和
     * ccService.getProcessCcRecords 均被调用。</p>
     */
    @Test
    void processCcRecordsRequireAdministrator() {
        UserContext.setCurrentUser("admin-id", "admin");
        when(ccService.getProcessCcRecords("process-1")).thenReturn(List.of());

        controller.getProcessCcRecords("process-1");

        verify(roleService).requireAdministrator("仅管理员可以查看流程全部知会记录");
        verify(ccService).getProcessCcRecords("process-1");
    }

    /**
     * 匿名用户查询流程知会记录应抛出 ForbiddenException 且不触发任何服务调用。
     */
    @Test
    void processCcRecordsRejectAnonymousUser() {
        assertThrows(ForbiddenException.class, () -> controller.getProcessCcRecords("process-1"));
        verifyNoInteractions(roleService, ccService);
    }
}
