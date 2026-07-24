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

/**
 * 流程动作执行记录控制器单元测试。
 *
 * <p>被测对象为 {@link FlowActionExecutionController}，验证非超级管理员
 * 查询执行日志时会被权限拦截，业务服务不被调用。</p>
 */
class FlowActionExecutionControllerTest {

    /**
     * 非超级管理员查询执行日志应被拒绝且不调用业务服务。
     *
     * <p>场景：roleService.requireSuperAdmin 抛出 ForbiddenException，
     * 断言控制器向上抛出异常且 executionService 未被调用。</p>
     */
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
