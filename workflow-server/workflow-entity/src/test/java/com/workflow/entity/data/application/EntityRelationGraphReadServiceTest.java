package com.workflow.entity.data.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.Grant;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.AccessMode;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.InternalPurpose;
import com.workflow.entity.data.application.EntityRelationProjectionReadPort.ProjectionPage;
import com.workflow.entity.data.application.EntityRelationProjectionReadPort.ProjectionQuery;
import com.workflow.entity.data.application.EntityRelationProjectionReadPort.ProjectionRow;
import com.workflow.entity.data.application.model.EntityRelationGraph;
import com.workflow.entity.data.application.model.EntityRelationGraph.Limits;
import com.workflow.entity.definition.application.PublishedRelationPathResolver;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.entity.definition.application.model.PublishedRelationPath.Hop;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkField;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkValueType;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRelationGraphReadServiceTest {

    private PublishedRelationPathResolver pathResolver;
    private EntityRelationProjectionReadPort projectionPort;
    private EntityRelationGraphReadService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("graph-user", "reader");
        pathResolver = mock(PublishedRelationPathResolver.class);
        projectionPort = mock(EntityRelationProjectionReadPort.class);
        service = new EntityRelationGraphReadService(
                pathResolver, projectionPort);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void readsTwoHopsWithPinnedProjectionAndPerHopScope() {
        PublishedRelationPath path = path(List.of(
                relationHop(1, "requirements", "project", "requirement",
                        "projectId", true),
                referenceHop(2, "customerId", "requirement", "customer",
                        LinkValueType.SCALAR_REFERENCE)));
        when(pathResolver.validate(path)).thenReturn(path);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("p-1", Map.of())), 1, 1, 1),
                        page(List.of(
                                row("r-1", Map.of(
                                        "projectId", "p-1",
                                        "customerId", "c-1")),
                                row("r-2", Map.of(
                                        "projectId", "p-1",
                                        "customerId", "c-2"))),
                                2, 1, 200),
                        page(List.of(
                                row("c-1", Map.of()),
                                row("c-2", Map.of())),
                                2, 1, 50));

        EntityRelationGraph result = service.read(
                path,
                List.of("p-1"),
                Limits.defaults(),
                authorization(path));

        assertEquals(5, result.nodes().size());
        assertEquals(4, result.edges().size());
        assertEquals(List.of("c-1", "c-2"),
                result.terminalRecords().stream()
                        .map(EntityRelationGraph.RecordRef::recordId)
                        .toList());
        assertFalse(result.truncated());

        ArgumentCaptor<ProjectionQuery> queries =
                ArgumentCaptor.forClass(ProjectionQuery.class);
        verify(projectionPort, atLeastOnce()).readPage(queries.capture());
        assertTrue(queries.getAllValues().stream()
                .allMatch(query -> query.dataScopePlan() != null));
        assertTrue(queries.getAllValues().stream()
                .noneMatch(query -> query.projectedFields().stream()
                        .anyMatch(field -> "secret".equals(field.fieldCode()))));
    }

    @Test
    void finalHopCanPageButIntermediateHopMustBeComplete() {
        PublishedRelationPath terminal = path(List.of(relationHop(
                1, "requirements", "project", "requirement",
                "projectId", true)));
        when(pathResolver.validate(terminal)).thenReturn(terminal);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("p-1", Map.of())), 1, 1, 1),
                        page(List.of(row("r-11", Map.of(
                                "projectId", "p-1"))), 21, 2, 10));

        EntityRelationGraph result = service.read(
                terminal,
                List.of("p-1"),
                limits(20, 100, 100, 100, 2, 10),
                authorization(terminal));
        assertTrue(result.truncated());
        assertEquals(21, result.terminalTotal());

        PublishedRelationPath twoHop = path(List.of(
                relationHop(1, "requirements", "project", "requirement",
                        "projectId", true),
                referenceHop(2, "customerId", "requirement", "customer",
                        LinkValueType.SCALAR_REFERENCE)));
        when(pathResolver.validate(twoHop)).thenReturn(twoHop);
        org.mockito.Mockito.reset(projectionPort);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("p-1", Map.of())), 1, 1, 1),
                        page(List.of(), 21, 1, 20));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.read(
                        twoHop,
                        List.of("p-1"),
                        limits(20, 100, 100, 100, 1, 10),
                        authorization(twoHop)));
        assertEquals("ENTITY_RELATION_GRAPH_LIMIT_EXCEEDED",
                exception.getErrorCode());
    }

    @Test
    void rejectsOneToOneCardinalityViolationBeforeTerminalPaging() {
        PublishedRelationPath path = path(List.of(relationHop(
                1, "finance", "project", "finance",
                "projectId", false)));
        when(pathResolver.validate(path)).thenReturn(path);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("p-1", Map.of())), 1, 1, 1),
                        page(List.of(
                                row("f-1", Map.of("projectId", "p-1")),
                                row("f-2", Map.of("projectId", "p-1"))),
                                2, 1, 200));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.read(
                        path,
                        List.of("p-1"),
                        Limits.defaults(),
                        authorization(path)));
        assertEquals("ENTITY_RELATION_CARDINALITY_VIOLATION",
                exception.getErrorCode());
    }

    @Test
    void allowsDiamondGraphWithoutTreatingSharedTargetAsCycle() {
        PublishedRelationPath path = path(List.of(
                relationHop(1, "branches", "root", "branch",
                        "rootId", true),
                referenceHop(2, "leafId", "branch", "leaf",
                        LinkValueType.SCALAR_REFERENCE)));
        when(pathResolver.validate(path)).thenReturn(path);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("root-1", Map.of())), 1, 1, 1),
                        page(List.of(
                                row("b-1", Map.of(
                                        "rootId", "root-1",
                                        "leafId", "leaf-1")),
                                row("b-2", Map.of(
                                        "rootId", "root-1",
                                        "leafId", "leaf-1"))),
                                2, 1, 200),
                        page(List.of(row("leaf-1", Map.of())), 1, 1, 50));

        EntityRelationGraph result = service.read(
                path,
                List.of("root-1"),
                Limits.defaults(),
                authorization(path));

        assertEquals(4, result.edges().size());
        assertEquals(List.of("leaf-1"),
                result.terminalRecords().stream()
                        .map(EntityRelationGraph.RecordRef::recordId)
                        .toList());
    }

    @Test
    void treatsMultiReferenceAccordingToPinnedFieldType() {
        PublishedRelationPath path = path(List.of(referenceHop(
                1, "customers", "requirement", "customer",
                LinkValueType.MULTI_REFERENCE)));
        when(pathResolver.validate(path)).thenReturn(path);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("r-1", Map.of(
                                "customers", List.of("c-1", "c-2")))),
                                1, 1, 1),
                        page(List.of(
                                row("c-1", Map.of()),
                                row("c-2", Map.of())),
                                2, 1, 50));

        EntityRelationGraph result = service.read(
                path,
                List.of("r-1"),
                Limits.defaults(),
                authorization(path));
        assertEquals(2, result.edges().size());

        org.mockito.Mockito.reset(projectionPort);
        when(projectionPort.readPage(any())).thenReturn(page(
                List.of(row("r-1", Map.of("customers", "[\"c-1\"]"))),
                1, 1, 1));
        BusinessConflictException invalid = assertThrows(
                BusinessConflictException.class,
                () -> service.read(
                        path,
                        List.of("r-1"),
                        Limits.defaults(),
                        authorization(path)));
        assertEquals("ENTITY_RELATION_REFERENCE_INVALID",
                invalid.getErrorCode());
    }

    @Test
    void rejectsCycleInaccessibleSourceAndTamperedAuthorization() {
        PublishedRelationPath cyclic = path(List.of(
                relationHop(1, "children", "node", "node",
                        "parentId", true),
                relationHop(2, "children", "node", "node",
                        "parentId", true)));
        when(pathResolver.validate(cyclic)).thenReturn(cyclic);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("a", Map.of())), 1, 1, 1),
                        page(List.of(row("b", Map.of("parentId", "a"))),
                                1, 1, 200),
                        page(List.of(row("a", Map.of("parentId", "b"))),
                                1, 1, 50));
        BusinessConflictException cycle = assertThrows(
                BusinessConflictException.class,
                () -> service.read(
                        cyclic,
                        List.of("a"),
                        Limits.defaults(),
                        authorization(cyclic)));
        assertEquals("ENTITY_RELATION_GRAPH_CYCLE", cycle.getErrorCode());

        PublishedRelationPath oneHop = path(List.of(relationHop(
                1, "children", "project", "requirement",
                "projectId", true)));
        when(pathResolver.validate(oneHop)).thenReturn(oneHop);
        org.mockito.Mockito.reset(projectionPort);
        when(projectionPort.readPage(any()))
                .thenReturn(page(List.of(), 0, 1, 1));
        assertThrows(ForbiddenException.class,
                () -> service.read(
                        oneHop,
                        List.of("missing"),
                        Limits.defaults(),
                        authorization(oneHop)));

        EntityRelationGraphAuthorizationPlan valid = authorization(oneHop);
        Grant wrongSource = new Grant(
                0,
                oneHop.sourceEntityCode(),
                "tampered-history",
                oneHop.sourceSchemaHash(),
                AccessMode.INTERNAL_SAFE,
                EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY,
                null,
                scope("source"));
        EntityRelationGraphAuthorizationPlan tampered =
                new IssuedEntityRelationGraphAuthorizationPlan(
                        "graph-user",
                        InternalPurpose.PROCESS_COORDINATION,
                        wrongSource,
                        valid.hops());
        assertThrows(ForbiddenException.class,
                () -> service.read(
                        oneHop,
                        List.of("p-1"),
                        Limits.defaults(),
                        tampered));
    }

    @Test
    void enforcesEdgeAndPathStateBudgetsBeforeDenseExpansion() {
        PublishedRelationPath path = path(List.of(referenceHop(
                1, "customers", "requirement", "customer",
                LinkValueType.MULTI_REFERENCE)));
        when(pathResolver.validate(path)).thenReturn(path);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(row("r-1", Map.of(
                                "customers", List.of("c-1", "c-2", "c-3")))),
                                1, 1, 1),
                        page(List.of(
                                row("c-1", Map.of()),
                                row("c-2", Map.of()),
                                row("c-3", Map.of())),
                                3, 1, 3));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.read(
                        path,
                        List.of("r-1"),
                        limits(3, 10, 2, 10, 1, 3),
                        authorization(path)));
        assertEquals("ENTITY_RELATION_GRAPH_LIMIT_EXCEEDED",
                exception.getErrorCode());
    }

    @Test
    void enforcesPathStateBudgetIndependentlyFromEdgeBudget() {
        PublishedRelationPath path = path(List.of(referenceHop(
                1, "customers", "requirement", "customer",
                LinkValueType.MULTI_REFERENCE)));
        when(pathResolver.validate(path)).thenReturn(path);
        when(projectionPort.readPage(any()))
                .thenReturn(
                        page(List.of(
                                row("r-1", Map.of("customers",
                                        List.of("c-1", "c-2"))),
                                row("r-2", Map.of("customers",
                                        List.of("c-1", "c-2")))),
                                2, 1, 2),
                        page(List.of(
                                row("c-1", Map.of()),
                                row("c-2", Map.of())),
                                2, 1, 2));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.read(
                        path,
                        List.of("r-1", "r-2"),
                        limits(2, 10, 10, 3, 1, 2),
                        authorization(path)));

        assertEquals("ENTITY_RELATION_GRAPH_LIMIT_EXCEEDED",
                exception.getErrorCode());
    }

    private EntityRelationGraphAuthorizationPlan authorization(
            PublishedRelationPath path) {
        Grant source = new Grant(
                0,
                path.sourceEntityCode(),
                path.sourceHistoryId(),
                path.sourceSchemaHash(),
                AccessMode.INTERNAL_SAFE,
                EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY,
                null,
                scope("source"));
        List<Grant> hops = path.hops().stream()
                .map(hop -> new Grant(
                        hop.index(),
                        hop.targetEntityCode(),
                        hop.targetHistoryId(),
                        hop.targetSchemaHash(),
                        AccessMode.INTERNAL_SAFE,
                        EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY,
                        null,
                        scope("hop-" + hop.index())))
                .toList();
        return new IssuedEntityRelationGraphAuthorizationPlan(
                "graph-user",
                InternalPurpose.PROCESS_COORDINATION,
                source,
                hops);
    }

    private DataScopePlan scope(String marker) {
        return new DataScopePlan(
                true,
                "owner_id = #{permissionParameters.owner}",
                Map.of("owner", marker),
                List.of(),
                List.of("explicit-allow"),
                "test",
                1);
    }

    private PublishedRelationPath path(List<Hop> hops) {
        Hop first = hops.get(0);
        return new PublishedRelationPath(
                first.sourceEntityCode(),
                first.sourceHistoryId(),
                first.sourceSchemaHash(),
                hops);
    }

    private Hop relationHop(
            int index,
            String code,
            String source,
            String target,
            String targetField,
            boolean multiple) {
        LinkField link = scalar(targetField, "id-" + source);
        return hop(index, StepType.RELATION, code, source, target,
                null, link, multiple);
    }

    private Hop referenceHop(
            int index,
            String field,
            String source,
            String target,
            LinkValueType type) {
        LinkField link = type == LinkValueType.MULTI_REFERENCE
                ? multi(field, "id-" + target)
                : scalar(field, "id-" + target);
        return hop(index, StepType.REFERENCE_FIELD, field, source, target,
                link, null, type == LinkValueType.MULTI_REFERENCE);
    }

    private Hop hop(
            int index,
            StepType type,
            String code,
            String source,
            String target,
            LinkField sourceLink,
            LinkField targetLink,
            boolean multiple) {
        return new Hop(
                index,
                type,
                code,
                source,
                "history-" + source,
                "hash-" + source,
                target,
                "history-" + target,
                "hash-" + target,
                sourceLink == null ? null : sourceLink.fieldCode(),
                targetLink == null ? null : targetLink.fieldCode(),
                type == StepType.RELATION ? code : null,
                type == StepType.RELATION ? "ASSOCIATION" : null,
                multiple,
                sourceLink,
                targetLink);
    }

    private LinkField scalar(String code, String targetEntityId) {
        return new LinkField(
                code,
                LinkValueType.SCALAR_REFERENCE,
                code.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                        .toLowerCase(),
                targetEntityId);
    }

    private LinkField multi(String code, String targetEntityId) {
        return new LinkField(
                code,
                LinkValueType.MULTI_REFERENCE,
                null,
                targetEntityId);
    }

    private ProjectionRow row(
            String id,
            Map<String, Object> links) {
        return new ProjectionRow(id, links);
    }

    private ProjectionPage page(
            List<ProjectionRow> rows,
            long total,
            long pageNum,
            long pageSize) {
        return new ProjectionPage(rows, total, pageNum, pageSize);
    }

    private Limits limits(
            int perHop,
            int total,
            int edges,
            int states,
            long pageNum,
            long pageSize) {
        return new Limits(perHop, total, edges, states, pageNum, pageSize);
    }
}
