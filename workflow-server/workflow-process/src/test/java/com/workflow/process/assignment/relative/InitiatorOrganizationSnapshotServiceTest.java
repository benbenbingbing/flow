package com.workflow.process.assignment.relative;

import com.workflow.contracts.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitiatorOrganizationSnapshotServiceTest {

    @Test
    void captureAcceptsIdOrUsernameAndOverwritesForgedSnapshot() {
        OrganizationPositionDirectoryPort directory =
                mock(OrganizationPositionDirectoryPort.class);
        InitiatorOrganizationSnapshot snapshot = snapshot();
        when(directory.captureInitiatorSnapshot("user-1"))
                .thenReturn(snapshot);
        when(directory.captureInitiatorSnapshot("zhangsan"))
                .thenReturn(snapshot);
        InitiatorOrganizationSnapshotService service =
                new InitiatorOrganizationSnapshotService(directory);
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> forged = Map.of("userId", "attacker");
        variables.put(
                InitiatorOrganizationSnapshotService.VARIABLE_NAME,
                forged);

        service.captureTrustedSnapshot(variables, "user-1");
        assertNotEquals(
                forged,
                variables.get(
                        InitiatorOrganizationSnapshotService.VARIABLE_NAME));
        assertEquals("user-1", service.requireSnapshot(variables).userId());

        service.captureTrustedSnapshot(variables, "zhangsan");
        assertEquals("zhangsan", service.requireSnapshot(variables).username());
        verify(directory).captureInitiatorSnapshot("user-1");
        verify(directory).captureInitiatorSnapshot("zhangsan");
    }

    @Test
    void duplicateFrozenUnitFailsWithStableCycleCode() {
        OrganizationPositionDirectoryPort directory =
                mock(OrganizationPositionDirectoryPort.class);
        when(directory.captureInitiatorSnapshot("user-1"))
                .thenReturn(new InitiatorOrganizationSnapshot(
                        1,
                        "user-1",
                        "zhangsan",
                        "org-1",
                        "dept-1",
                        List.of(
                                new OrganizationUnitSnapshot(
                                        "dept-1", "部门", "dept", null),
                                new OrganizationUnitSnapshot(
                                        "org-1", "组织", "org", null),
                                new OrganizationUnitSnapshot(
                                        "dept-1", "重复部门", "dept", null)),
                        Instant.parse("2026-08-27T02:00:00Z")));
        InitiatorOrganizationSnapshotService service =
                new InitiatorOrganizationSnapshotService(directory);

        PersonResolutionException failure = assertThrows(
                PersonResolutionException.class,
                () -> service.captureTrustedSnapshot(
                        new LinkedHashMap<>(), "user-1"));

        assertEquals("HIERARCHY_CYCLE", failure.reasonCode());
    }

    private InitiatorOrganizationSnapshot snapshot() {
        return new InitiatorOrganizationSnapshot(
                1,
                "user-1",
                "zhangsan",
                "org-1",
                "dept-1",
                List.of(
                        new OrganizationUnitSnapshot(
                                "dept-1", "三级部门", "dept", "TEAM"),
                        new OrganizationUnitSnapshot(
                                "dept-parent", "一级部门", "dept",
                                "FIRST_LEVEL_DEPT"),
                        new OrganizationUnitSnapshot(
                                "org-1", "组织", "org", "COMPANY")),
                Instant.parse("2026-08-27T02:00:00Z"));
    }
}
