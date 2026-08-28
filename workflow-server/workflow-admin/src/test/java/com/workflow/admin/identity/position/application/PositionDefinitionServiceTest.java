package com.workflow.admin.identity.position.application;

import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.PositionManagementException;
import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import com.workflow.admin.security.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionDefinitionServiceTest {

    private final SysPositionMapper positionMapper =
            mock(SysPositionMapper.class);
    private final PositionDefinitionService service =
            new PositionDefinitionService(
                    positionMapper,
                    mock(PositionOrganizationScopeService.class));

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("actor-1", "operator");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void builtInLeaderPositionCannotBeDisabled() {
        SysPosition position = new SysPosition();
        position.setId("position-unit-leader");
        position.setPositionCode(PositionAssignmentService.UNIT_LEADER);
        position.setBuiltIn(true);
        position.setStatus(SysPosition.Status.ENABLED.name());
        position.setRevision(3);
        when(positionMapper.selectForUpdate(position.getId()))
                .thenReturn(position);

        PositionManagementException exception = assertThrows(
                PositionManagementException.class,
                () -> service.changeStatus(
                        position.getId(),
                        new PositionRequests.ChangePositionStatus(
                                SysPosition.Status.DISABLED.name(), 3)));

        assertEquals(PositionErrorCode.POSITION_REFERENCED,
                exception.errorCode());
        verify(positionMapper, never()).updateStatus(
                position.getId(), SysPosition.Status.DISABLED.name(),
                "actor-1", 3);
    }
}
