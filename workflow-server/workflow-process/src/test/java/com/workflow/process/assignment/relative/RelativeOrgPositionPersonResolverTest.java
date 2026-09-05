package com.workflow.process.assignment.relative;

import com.workflow.contracts.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.port.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.OrganizationPositionErrorCode;
import com.workflow.contracts.identity.position.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.position.OrganizationUnitStateView;
import com.workflow.contracts.identity.position.PositionDefinitionView;
import com.workflow.contracts.identity.position.PositionDirectoryResultCode;
import com.workflow.contracts.identity.position.PositionHolderResolution;
import com.workflow.contracts.identity.position.PositionHolderView;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelativeOrgPositionPersonResolverTest {

    private OrganizationPositionDirectoryPort directory;
    private InitiatorOrganizationSnapshotService snapshotService;
    private RelativeOrgPositionPersonResolver resolver;

    @BeforeEach
    void setUp() {
        directory = mock(OrganizationPositionDirectoryPort.class);
        snapshotService = new InitiatorOrganizationSnapshotService(
                directory);
        resolver = new RelativeOrgPositionPersonResolver(
                directory, snapshotService);
        when(directory.requireEnabledPosition("UNIT_LEADER"))
                .thenReturn(new PositionDefinitionView(
                        "UNIT_LEADER", "负责人", "ANY", "MULTIPLE", 1));
    }

    @Test
    void lowerCaseBusinessLevelAndPositionResolveAgainstCanonicalDirectoryCodes() {
        InitiatorOrganizationSnapshot snapshot = snapshot();
        Map<String, Object> variables = captured(snapshot, "zhangsan");
        when(directory.requireActiveOrganizationUnit(any()))
                .thenAnswer(invocation -> {
                    String id = invocation.getArgument(0);
                    String type = "org-1".equals(id) ? "org" : "dept";
                    return new OrganizationUnitStateView(
                            id, type, "revision-7");
                });
        when(directory.findEffectiveHolders(
                org.mockito.ArgumentMatchers.eq("UNIT_LEADER"),
                org.mockito.ArgumentMatchers.eq("dept-first"),
                any(Instant.class)))
                .thenReturn(resolved(
                        "dept-first", holder("leader", true)));

        var result = resolver.resolve(request(
                "zhangsan",
                variables,
                config(
                        "unit_leader",
                        Map.of(
                                "mode", "BUSINESS_LEVEL",
                                "businessLevelCode", "first_level_dept"),
                        "ERROR")));

        assertEquals(1, result.principals().size());
        assertEquals("leader", result.principals().get(0).key());
        verify(directory).requireEnabledPosition("UNIT_LEADER");
        verify(directory).findEffectiveHolders(
                org.mockito.ArgumentMatchers.eq("UNIT_LEADER"),
                org.mockito.ArgumentMatchers.eq("dept-first"),
                any(Instant.class));
    }

    @Test
    void nearestValidatesEverySkippedLevelBeforeStartLevel() {
        Map<String, Object> variables = captured(snapshot(), "user-1");
        when(directory.requireActiveOrganizationUnit("dept-team"))
                .thenReturn(new OrganizationUnitStateView(
                        "dept-team", "dept", "revision-1"));
        when(directory.requireActiveOrganizationUnit("dept-mid"))
                .thenThrow(new OrganizationPositionDirectoryException(
                        OrganizationPositionErrorCode
                                .ORGANIZATION_UNIT_DISABLED,
                        "中间部门已停用"));

        PersonResolutionException failure = assertThrows(
                PersonResolutionException.class,
                () -> resolver.resolve(request(
                        "user-1",
                        variables,
                        config(
                                "UNIT_LEADER",
                                Map.of(
                                        "mode", "NEAREST_WITH_HOLDER",
                                        "startLevel", 2,
                                        "maxHops", 3),
                                "ALL"))));

        assertEquals("ORG_SNAPSHOT_INVALID", failure.reasonCode());
        verify(directory).requireActiveOrganizationUnit("dept-team");
        verify(directory).requireActiveOrganizationUnit("dept-mid");
        verify(directory, never()).findEffectiveHolders(
                any(), any(), any());
    }

    @Test
    void fixedAncestorPreviewReportsTheRealDepth() {
        InitiatorOrganizationSnapshot snapshot = snapshot();
        when(directory.captureInitiatorSnapshot("user-1"))
                .thenReturn(snapshot);
        when(directory.requireActiveOrganizationUnit(any()))
                .thenAnswer(invocation -> new OrganizationUnitStateView(
                        invocation.getArgument(0), "dept", "revision-9"));
        when(directory.findEffectiveHolders(
                org.mockito.ArgumentMatchers.eq("UNIT_LEADER"),
                org.mockito.ArgumentMatchers.eq("dept-first"),
                any(Instant.class)))
                .thenReturn(resolved(
                        "dept-first", holder("leader", false)));

        RelativeOrgPositionPreview preview = resolver.preview(
                "user-1",
                config(
                        "UNIT_LEADER",
                        Map.of(
                                "mode", "FIXED_ANCESTOR",
                                "ancestorHops", 2),
                        "ALL"));

        assertEquals(2, preview.matchedUnit().depth());
        assertTrue(preview.scannedUnits().stream().anyMatch(
                item -> item.depth() == 2
                        && "RESOLVED".equals(item.result())));
    }

    private Map<String, Object> captured(
            InitiatorOrganizationSnapshot snapshot,
            String idOrUsername) {
        when(directory.captureInitiatorSnapshot(idOrUsername))
                .thenReturn(snapshot);
        Map<String, Object> variables = new LinkedHashMap<>();
        snapshotService.captureTrustedSnapshot(
                variables, idOrUsername);
        return variables;
    }

    private PersonResolveRequest request(
            String initiator,
            Map<String, Object> variables,
            Map<String, Object> config) {
        return new PersonResolveRequest(
                1,
                "trace-1",
                "request-1",
                PersonResolveUsage.ASSIGNEE,
                "process-config-1",
                "definition-1",
                "instance-1",
                "business-1",
                "approve",
                "审批",
                null,
                null,
                null,
                initiator,
                null,
                variables,
                Map.of(),
                config);
    }

    private Map<String, Object> config(
            String positionCode,
            Map<String, Object> hierarchy,
            String multiplePolicy) {
        return Map.of(
                "schemaVersion", 1,
                "subject", "PROCESS_INITIATOR",
                "anchor", "DEPARTMENT",
                "positionCode", positionCode,
                "hierarchy", hierarchy,
                "multipleMatchPolicy", multiplePolicy);
    }

    private InitiatorOrganizationSnapshot snapshot() {
        return new InitiatorOrganizationSnapshot(
                1,
                "user-1",
                "zhangsan",
                "org-1",
                "dept-team",
                List.of(
                        new OrganizationUnitSnapshot(
                                "dept-team", "团队", "dept", "TEAM"),
                        new OrganizationUnitSnapshot(
                                "dept-mid", "二级部门", "dept",
                                "SECOND_LEVEL_DEPT"),
                        new OrganizationUnitSnapshot(
                                "dept-first", "一级部门", "dept",
                                "FIRST_LEVEL_DEPT"),
                        new OrganizationUnitSnapshot(
                                "org-1", "组织", "org", "COMPANY")),
                Instant.parse("2026-08-27T02:00:00Z"));
    }

    private PositionHolderView holder(
            String username,
            boolean primary) {
        return new PositionHolderView(
                "id-" + username,
                username,
                username,
                primary,
                1,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private PositionHolderResolution resolved(
            String unitId,
            PositionHolderView... holders) {
        return new PositionHolderResolution(
                PositionDirectoryResultCode.RESOLVED,
                "UNIT_LEADER",
                unitId,
                List.of(holders),
                "revision-10");
    }
}
