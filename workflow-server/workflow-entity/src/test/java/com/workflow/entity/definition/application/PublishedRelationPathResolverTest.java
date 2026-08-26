package com.workflow.entity.definition.application;

import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService.PinnedEntitySnapshot;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.entity.definition.application.model.PublishedRelationPath.Hop;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkValueType;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepSpec;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepType;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishedRelationPathResolverTest {

    private EntityPublishedSnapshotService snapshotService;
    private PublishedRelationPathResolver resolver;

    @BeforeEach
    void setUp() {
        snapshotService = mock(EntityPublishedSnapshotService.class);
        resolver = new PublishedRelationPathResolver(snapshotService);
    }

    @Test
    void compilesRelationAndRuntimeKeepsExactTargetRelease() {
        EntityPublishedSnapshot project = snapshot(
                "project-id", "project", "project-history", 3);
        EntityPublishedSnapshot requirement = snapshot(
                "requirement-id", "requirement", "requirement-history", 7);
        project.setRelationsSnapshotAvailable(true);
        project.setRelations(List.of(relation(
                "requirements", requirement, "projectId")));
        requirement.setFields(List.of(reference(
                "projectId", project.getEntityId(), false)));
        when(snapshotService.getPinnedByHistoryId("project-history"))
                .thenReturn(new PinnedEntitySnapshot(project, "project-hash"));
        when(snapshotService.getLatestPinnedByEntityId("requirement-id"))
                .thenReturn(new PinnedEntitySnapshot(
                        requirement, "requirement-hash"));

        PublishedRelationPath path = resolver.compile(
                "project-history",
                List.of(new StepSpec(
                        StepType.RELATION, "requirements", null)));

        assertEquals("requirement-history",
                path.hops().get(0).targetHistoryId());
        assertEquals("projectId", path.hops().get(0).targetFieldCode());
        assertEquals(LinkValueType.SCALAR_REFERENCE,
                path.hops().get(0).targetLinkField().valueType());

        // 运行时只按路径中的 exact history 校验；目标后来出现新 latest 不能让
        // 已发布宿主失效或静默改用新版本。
        reset(snapshotService);
        when(snapshotService.getPinnedByHistoryId("project-history"))
                .thenReturn(new PinnedEntitySnapshot(project, "project-hash"));
        when(snapshotService.getPinnedByHistoryId("requirement-history"))
                .thenReturn(new PinnedEntitySnapshot(
                        requirement, "requirement-hash"));

        resolver.validate(path);

        verify(snapshotService, never())
                .getLatestPinnedByEntityId("requirement-id");
    }

    @Test
    void compilesReferenceThenReverseReference() {
        EntityPublishedSnapshot requirement = snapshot(
                "requirement-id", "requirement", "req-history", 1);
        EntityPublishedSnapshot project = snapshot(
                "project-id", "project", "project-history", 2);
        EntityPublishedSnapshot risk = snapshot(
                "risk-id", "risk", "risk-history", 4);
        requirement.setFields(List.of(reference(
                "projectId", project.getEntityId(), false)));
        risk.setFields(List.of(reference(
                "projectId", project.getEntityId(), false)));
        when(snapshotService.getPinnedByHistoryId("req-history"))
                .thenReturn(new PinnedEntitySnapshot(requirement, "req-hash"));
        when(snapshotService.getLatestPinnedByEntityId("project-id"))
                .thenReturn(new PinnedEntitySnapshot(project, "project-hash"));
        when(snapshotService.getLatestPinnedByEntityId("risk-id"))
                .thenReturn(new PinnedEntitySnapshot(risk, "risk-hash"));

        PublishedRelationPath path = resolver.compile(
                "req-history",
                List.of(
                        new StepSpec(
                                StepType.REFERENCE_FIELD,
                                "projectId",
                                null),
                        new StepSpec(
                                StepType.REVERSE_REFERENCE,
                                "projectId",
                                "risk-id")));

        assertEquals(List.of("project", "risk"), path.hops().stream()
                .map(PublishedRelationPath.Hop::targetEntityCode)
                .toList());
        assertEquals("projectId", path.hops().get(0).sourceFieldCode());
        assertEquals("projectId", path.hops().get(1).targetFieldCode());
    }

    @Test
    void rejectsReverseFieldThatDoesNotReferenceCurrentEntity() {
        EntityPublishedSnapshot project = snapshot(
                "project-id", "project", "project-history", 1);
        EntityPublishedSnapshot risk = snapshot(
                "risk-id", "risk", "risk-history", 1);
        risk.setFields(List.of(reference("customerId", "customer-id", false)));
        when(snapshotService.getPinnedByHistoryId("project-history"))
                .thenReturn(new PinnedEntitySnapshot(project, "project-hash"));
        when(snapshotService.getLatestPinnedByEntityId("risk-id"))
                .thenReturn(new PinnedEntitySnapshot(risk, "risk-hash"));

        assertThrows(IllegalArgumentException.class, () -> resolver.compile(
                "project-history",
                List.of(new StepSpec(
                        StepType.REVERSE_REFERENCE,
                        "customerId",
                        "risk-id"))));
    }

    @Test
    void rejectsLegacyRelationWithoutFrozenSnapshotAndOverDepth() {
        EntityPublishedSnapshot source = snapshot(
                "source-id", "source", "source-history", 1);
        source.setRelationsSnapshotAvailable(false);
        when(snapshotService.getPinnedByHistoryId("source-history"))
                .thenReturn(new PinnedEntitySnapshot(source, "source-hash"));

        assertThrows(IllegalArgumentException.class, () -> resolver.compile(
                "source-history",
                List.of(new StepSpec(StepType.RELATION, "children", null))));
        assertThrows(IllegalArgumentException.class, () -> resolver.compile(
                "source-history",
                java.util.stream.IntStream.range(0, 9)
                        .mapToObj(index -> new StepSpec(
                                StepType.RELATION, "r" + index, null))
                        .toList()));
    }

    @Test
    void relationRequiresPinnedScalarChildReferenceToSourceEntity() {
        EntityPublishedSnapshot project = snapshot(
                "project-id", "project", "project-history", 1);
        EntityPublishedSnapshot requirement = snapshot(
                "requirement-id", "requirement", "requirement-history", 1);
        project.setRelations(List.of(relation(
                "requirements", requirement, "projectId")));
        when(snapshotService.getPinnedByHistoryId("project-history"))
                .thenReturn(new PinnedEntitySnapshot(project, "project-hash"));
        when(snapshotService.getLatestPinnedByEntityId("requirement-id"))
                .thenReturn(new PinnedEntitySnapshot(
                        requirement, "requirement-hash"));

        EntityField multi = reference(
                "projectId", project.getEntityId(), true);
        requirement.setFields(List.of(multi));
        assertThrows(IllegalArgumentException.class, () -> resolver.compile(
                "project-history",
                List.of(new StepSpec(
                        StepType.RELATION, "requirements", null))));

        EntityField wrongTarget = reference(
                "projectId", "customer-id", false);
        requirement.setFields(List.of(wrongTarget));
        assertThrows(IllegalArgumentException.class, () -> resolver.compile(
                "project-history",
                List.of(new StepSpec(
                        StepType.RELATION, "requirements", null))));

        EntityField compatibleLegacy = reference(
                "projectId", project.getEntityId(), false);
        compatibleLegacy.setRefEntityType(null);
        requirement.setFields(List.of(compatibleLegacy));
        PublishedRelationPath path = resolver.compile(
                "project-history",
                List.of(new StepSpec(
                        StepType.RELATION, "requirements", null)));
        assertEquals("project_id",
                path.hops().get(0).targetLinkField().storageColumn());
    }

    @Test
    void rejectsNullStepAndTamperedPinnedLinkProjection() {
        EntityPublishedSnapshot requirement = snapshot(
                "requirement-id", "requirement", "req-history", 1);
        EntityPublishedSnapshot project = snapshot(
                "project-id", "project", "project-history", 1);
        requirement.setFields(List.of(reference(
                "projectId", project.getEntityId(), false)));
        when(snapshotService.getPinnedByHistoryId("req-history"))
                .thenReturn(new PinnedEntitySnapshot(requirement, "req-hash"));
        when(snapshotService.getLatestPinnedByEntityId("project-id"))
                .thenReturn(new PinnedEntitySnapshot(project, "project-hash"));

        assertThrows(IllegalArgumentException.class, () -> resolver.compile(
                "req-history",
                java.util.Arrays.asList((StepSpec) null)));

        PublishedRelationPath valid = resolver.compile(
                "req-history",
                List.of(new StepSpec(
                        StepType.REFERENCE_FIELD, "projectId", null)));
        Hop original = valid.hops().get(0);
        Hop tamperedHop = new Hop(
                original.index(),
                original.type(),
                original.code(),
                original.sourceEntityCode(),
                original.sourceHistoryId(),
                original.sourceSchemaHash(),
                original.targetEntityCode(),
                original.targetHistoryId(),
                original.targetSchemaHash(),
                original.sourceFieldCode(),
                original.targetFieldCode(),
                original.relationCode(),
                original.ownershipType(),
                original.multiple(),
                new PublishedRelationPath.LinkField(
                        "projectId",
                        LinkValueType.SCALAR_REFERENCE,
                        "attacker_column",
                        project.getEntityId()),
                null);
        PublishedRelationPath tampered = new PublishedRelationPath(
                valid.sourceEntityCode(),
                valid.sourceHistoryId(),
                valid.sourceSchemaHash(),
                List.of(tamperedHop));

        reset(snapshotService);
        when(snapshotService.getPinnedByHistoryId("req-history"))
                .thenReturn(new PinnedEntitySnapshot(requirement, "req-hash"));
        when(snapshotService.getPinnedByHistoryId("project-history"))
                .thenReturn(new PinnedEntitySnapshot(project, "project-hash"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validate(tampered));
    }

    private EntityPublishedSnapshot snapshot(
            String entityId,
            String entityCode,
            String historyId,
            int version) {
        EntityPublishedSnapshot value = new EntityPublishedSnapshot();
        value.setEntityId(entityId);
        value.setEntityCode(entityCode);
        value.setEntityName(entityCode);
        value.setHistoryId(historyId);
        value.setVersion(version);
        value.setFields(List.of());
        value.setRelations(List.of());
        value.setRelationsSnapshotAvailable(true);
        return value;
    }

    private EntityRelation relation(
            String code,
            EntityPublishedSnapshot target,
            String refField) {
        EntityRelation value = new EntityRelation();
        value.setRelationCode(code);
        value.setRelationName(code);
        value.setChildEntityId(target.getEntityId());
        value.setChildEntityCode(target.getEntityCode());
        value.setChildRefFieldCode(refField);
        value.setRelationType(EntityRelation.RelationType.ONE_TO_MANY);
        value.setOwnershipType(EntityRelation.OwnershipType.ASSOCIATION);
        value.setEnabled(true);
        return value;
    }

    private EntityField reference(
            String code,
            String targetEntityId,
            boolean multiple) {
        EntityField value = new EntityField();
        value.setFieldCode(code);
        value.setFieldType(multiple
                ? EntityField.FieldType.MULTI_REFERENCE
                : EntityField.FieldType.REFERENCE);
        value.setRefEntityType(EntityField.RefEntityType.CUSTOM);
        value.setRefEntityId(targetEntityId);
        value.setIsPublished(true);
        return value;
    }
}
