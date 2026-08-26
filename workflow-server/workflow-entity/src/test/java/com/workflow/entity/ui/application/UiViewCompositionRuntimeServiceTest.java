package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiViewCompositionRuntimeServiceTest {

    @Mock
    private UiConfigReleaseMapper releaseMapper;
    @Mock
    private UiConfigReleaseService releaseService;
    @Mock
    private EntityFormMapper formMapper;
    @Mock
    private EntityListConfigMapper listMapper;
    @Mock
    private EntityDefinitionMapper entityMapper;
    @Mock
    private EntityPublishedSnapshotService entitySnapshotService;
    @Mock
    private EntityDataDynamicService dynamicDataService;
    @Mock
    private SystemEntityReadService systemEntityReadService;
    @Mock
    private EntityActionCapabilityService capabilityService;
    @Mock
    private UiDataSourceService dataSourceService;
    @Mock
    private UiReleaseResolutionTokenService releaseTokenService;
    @Mock
    private UiViewCompositionTokenService compositionTokenService;
    private UiViewCompositionRuntimeService service;

    @Test
    void actionOnlySpecialHandlingDoesNotReplaceStandardDataResolution() {
        boolean result = ReflectionTestUtils.invokeMethod(
                service,
                "usesInterfaceService",
                Map.of("type", "REVERSE_REFERENCE"),
                Map.of(
                        "mode", "INTERFACE_SERVICE",
                        "interfaceService", Map.of(),
                        "actionServices", List.of(Map.of(
                                "actionKey", "CHECK_RISK"))));

        assertFalse(result);
    }

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("runtime-user", "reader");
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId("runtime-user"))
                .thenReturn(Set.of("*"));
        ReflectionTestUtils.setField(
                PermissionUtil.class,
                "staticMenuMapper",
                menuMapper);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new UiViewCompositionRuntimeService(
                releaseMapper,
                releaseService,
                formMapper,
                listMapper,
                entityMapper,
                entitySnapshotService,
                dynamicDataService,
                systemEntityReadService,
                capabilityService,
                dataSourceService,
                releaseTokenService,
                compositionTokenService,
                new JsonDocumentCodec(objectMapper),
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SAME_RECORD",
            "REFERENCE_FIELD",
            "REVERSE_REFERENCE",
            "FIELD_MATCH",
            "ENTITY_RELATION"
    })
    void standardRelationProducesServerOwnedListFilter(
            String relationType) {
        Fixture fixture = fixture(relationType, "LIST", true);

        UiViewCompositionResolveResponse response =
                service.resolve(fixture.request());

        assertEquals(
                fixture.expectedFilters(),
                response.getFixedFilters());
        assertEquals("target-list-release", response.getTargetReleaseId());
        assertEquals("list-context-token", response.getListContextToken());
        assertEquals(
                "traversal-context-token",
                response.getTraversalContextToken());
        assertEquals("source-record", response.getSourceRecordId());
        assertEquals("row-context-token", response.getRowContextToken());
        assertEquals(
                response.getRowContextToken(),
                response.getActionContextToken());
        verify(entitySnapshotService, never())
                .getLatestByEntityCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void signedPinnedFormOwnerReleaseRemainsResolvableAfterRepublish() {
        Fixture fixture = fixture("REVERSE_REFERENCE", "LIST", true);
        fixture.request().setReleaseResolutionToken(
                "signed-old-release-token");
        fixture.ownerRelease().setStatus("INACTIVE");
        when(releaseService.resolveRuntimeEventSnapshot(
                "owner-form",
                "owner-release",
                4,
                "signed-old-release-token"))
                .thenReturn(new UiConfigReleaseService
                        .ResolvedUiEventSnapshot(
                                fixture.ownerSnapshot(),
                                "owner-release",
                                4,
                                "owner-release",
                                false));

        UiViewCompositionResolveResponse response =
                service.resolve(fixture.request());

        assertEquals("owner-release", response.getReleaseId());
        assertEquals(
                fixture.expectedFilters(),
                response.getFixedFilters());
        verify(releaseService).resolveRuntimeEventSnapshot(
                "owner-form",
                "owner-release",
                4,
                "signed-old-release-token");
        verify(releaseService, never()).resolveRuntimeEventSnapshot(
                "owner-form", "owner-release", 4, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tampered-release-token",
            "expired-release-token"
    })
    void invalidPinnedFormOwnerReleaseTokenFailsClosed(String token) {
        Fixture fixture = fixture("REVERSE_REFERENCE", "LIST", true);
        fixture.request().setReleaseResolutionToken(token);
        String reason = token.startsWith("tampered")
                ? "表单发布解析令牌签名无效"
                : "表单发布解析令牌已过期";
        when(releaseService.resolveRuntimeEventSnapshot(
                "owner-form", "owner-release", 4, token))
                .thenThrow(new BusinessForbiddenException(
                        "INVALID_RELEASE_RESOLUTION_TOKEN",
                        reason));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> service.resolve(fixture.request()));

        assertEquals(
                "INVALID_RELEASE_RESOLUTION_TOKEN",
                exception.getErrorCode());
        assertEquals(reason, exception.getMessage());
        verify(dynamicDataService, never()).findAccessibleById(
                "source_entity", "source-record", null);
        verify(releaseService, never()).resolveRuntimeEventSnapshot(
                "owner-form", "owner-release", 4, null);
    }

    @Test
    void disabledPublishedCompositionCannotBeResolvedDirectly() {
        Fixture fixture = fixture("REVERSE_REFERENCE", "LIST", false);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.resolve(fixture.request()));

        assertEquals("VIEW_COMPOSITION_DISABLED", exception.getErrorCode());
    }

    @Test
    void legacyCompositionWithoutEntityPinsFailsClosed() {
        EntityDefinition source = entity(
                "source-entity-id", "source_entity");
        EntityDefinition target = entity(
                "target-entity-id", "target_entity");

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "requirePinnedEntitySchemas",
                        Map.of(),
                        source,
                        target));

        assertEquals(
                "VIEW_COMPOSITION_ENTITY_SNAPSHOT_REQUIRED",
                failure.getErrorCode());
    }

    @Test
    void formTargetRejectsMultipleMatches() {
        Fixture fixture = fixture("FIELD_MATCH", "FORM", true);
        EntityDataDTO first = record("target-1", Map.of());
        EntityDataDTO second = record("target-2", Map.of());
        when(dynamicDataService.findPage(
                "target_entity",
                null,
                Map.of(
                        "businessCode", "BIZ-001",
                        "businessCode_op", "EQ"),
                1,
                2)).thenReturn(new PageResult<>(
                        List.of(first, second), 2, 1, 2));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.resolve(fixture.request()));

        assertEquals(
                "VIEW_COMPOSITION_FORM_CARDINALITY_CONFLICT",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("多条记录"));
    }

    @Test
    void interfaceFiltersUseControlledOperatorsAndBoundedInValues() {
        EntityPublishedSnapshot target = snapshot(
                entity("target-entity-id", "target_entity"),
                "target-history",
                1,
                List.of(),
                List.of());
        Map<String, Object> normalized =
                ReflectionTestUtils.invokeMethod(
                        service,
                        "normalizeInterfaceFilters",
                        Map.of("id", List.of("target-1", "target-2")));

        assertEquals("IN", normalized.get("id_op"));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                service,
                "validateTargetFilters",
                target,
                normalized));

        Map<String, Object> arbitraryOperator = Map.of(
                "id", "target-1",
                "id_op", "RAW_EXPRESSION");
        assertThrows(
                BusinessConflictException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "validateTargetFilters",
                        target,
                        arbitraryOperator));

        List<Integer> tooMany = IntStream.range(0, 201)
                .boxed()
                .toList();
        Map<String, Object> oversized =
                ReflectionTestUtils.invokeMethod(
                        service,
                        "normalizeInterfaceFilters",
                        Map.of("id", tooMany));
        assertThrows(
                BusinessConflictException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "validateTargetFilters",
                        target,
                        oversized));
    }

    @Test
    void runtimeRejectsTamperedPinnedComponentDefinition() {
        JsonDocumentCodec codec = new JsonDocumentCodec(
                new ObjectMapper());
        String snapshot = codec.canonicalize(
                codec.write(Map.of(
                        "schemaVersion", 1,
                        "extensionKey", "project-timeline",
                        "extensionType", "FORM",
                        "version", 2,
                        "snapshotVersion", 5,
                        "status", "ACTIVE"),
                        "component definition"),
                "component definition");
        Map<String, Object> special = Map.of(
                "customComponent", Map.of(
                        "name", "project-timeline",
                        "extensionType", "FORM",
                        "version", 2,
                        "snapshotVersion", 5,
                        "definitionSnapshot", snapshot,
                        "definitionHash", "0".repeat(64)));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "validatePinnedCustomComponent",
                        special));

        assertEquals(
                "VIEW_COMPOSITION_COMPONENT_SNAPSHOT_TAMPERED",
                failure.getErrorCode());
    }

    @Test
    void runtimeRejectsPinnedComponentArtifactDrift() {
        JsonDocumentCodec codec = new JsonDocumentCodec(
                new ObjectMapper());
        String snapshot = codec.canonicalize(
                codec.write(Map.of(
                                "schemaVersion", 2,
                                "extensionKey", "project-timeline",
                                "extensionType", "FORM",
                                "version", 2,
                                "snapshotVersion", 5,
                                "artifactDigest", "a".repeat(64),
                                "status", "ACTIVE"),
                        "component definition"),
                "component definition");
        String hash = ReflectionTestUtils.invokeMethod(
                service, "sha256", snapshot);
        Map<String, Object> special = Map.of(
                "customComponent", Map.of(
                        "name", "project-timeline",
                        "extensionType", "FORM",
                        "version", 2,
                        "snapshotVersion", 5,
                        "artifactDigest", "b".repeat(64),
                        "definitionSnapshot", snapshot,
                        "definitionHash", hash));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "validatePinnedCustomComponent",
                        special));

        assertEquals(
                "VIEW_COMPOSITION_COMPONENT_ARTIFACT_CONFLICT",
                failure.getErrorCode());
    }

    /**
     * 创建一组最小的发布快照和服务端记录。测试只向请求放 recordId，
     * 关联值全部来自 findAccessibleById 返回的可信记录。
     */
    private Fixture fixture(
            String relationType,
            String targetContentType,
            boolean enabled) {
        boolean sameRecord = "SAME_RECORD".equals(relationType);
        String targetEntityId = sameRecord
                ? "source-entity-id" : "target-entity-id";
        String targetEntityCode = sameRecord
                ? "source_entity" : "target_entity";
        String targetContentId = "FORM".equals(targetContentType)
                ? "target-form" : "target-list";
        String targetReleaseId = "FORM".equals(targetContentType)
                ? "target-form-release" : "target-list-release";

        EntityDefinition sourceEntity = entity(
                "source-entity-id", "source_entity");
        EntityDefinition targetEntity = sameRecord
                ? sourceEntity
                : entity(targetEntityId, targetEntityCode);
        when(entityMapper.selectById("source-entity-id"))
                .thenReturn(sourceEntity);
        if (!sameRecord) {
            when(entityMapper.selectById(targetEntityId))
                    .thenReturn(targetEntity);
        }

        EntityDataDTO sourceRecord = record(
                "source-record",
                Map.of(
                        "projectRef", "target-record",
                        "businessCode", "BIZ-001"));
        when(dynamicDataService.findAccessibleById(
                "source_entity", "source-record", null))
                .thenReturn(sourceRecord);

        EntityPublishedSnapshot sourceSchema = snapshot(
                sourceEntity,
                "source-history",
                3,
                List.of(
                        field(
                                "projectRef",
                                EntityField.FieldType.REFERENCE,
                                "target-entity-id"),
                        field(
                                "businessCode",
                                EntityField.FieldType.STRING,
                                null)),
                relationType.equals("ENTITY_RELATION")
                        ? List.of(relation()) : List.of());
        EntityPublishedSnapshot targetSchema = sameRecord
                ? sourceSchema
                : snapshot(
                        targetEntity,
                        "target-history",
                        5,
                        List.of(
                                field(
                                        "projectId",
                                        EntityField.FieldType.REFERENCE,
                                        "source-entity-id"),
                                field(
                                        "businessCode",
                                        EntityField.FieldType.STRING,
                                        null)),
                        List.of());
        when(entitySnapshotService.getPinnedByHistoryId("source-history"))
                .thenReturn(new EntityPublishedSnapshotService
                        .PinnedEntitySnapshot(sourceSchema, "a".repeat(64)));
        if (!sameRecord) {
            when(entitySnapshotService.getPinnedByHistoryId("target-history"))
                    .thenReturn(new EntityPublishedSnapshotService
                            .PinnedEntitySnapshot(targetSchema, "b".repeat(64)));
        }

        Map<String, Object> relationConfig = switch (relationType) {
            case "REFERENCE_FIELD" -> Map.of(
                    "type", relationType,
                    "sourceField", "projectRef");
            case "REVERSE_REFERENCE" -> Map.of(
                    "type", relationType,
                    "targetField", "projectId");
            case "FIELD_MATCH" -> Map.of(
                    "type", relationType,
                    "mappings", List.of(Map.of(
                            "sourceField", "businessCode",
                            "targetField", "businessCode")));
            case "ENTITY_RELATION" -> Map.of(
                    "type", relationType,
                    "relationCode", "project-requirements");
            default -> Map.of("type", relationType);
        };
        Map<String, Object> targetConfig = Map.of(
                "entityId", targetEntityId,
                "contentType", targetContentType,
                "contentId", targetContentId,
                "releaseId", targetReleaseId,
                "releaseVersion", 2,
                "contentHash", "target-content-hash");
        Map<String, Object> sourcePin = Map.of(
                "historyId", "source-history",
                "entityId", "source-entity-id",
                "entityCode", "source_entity",
                "version", 3,
                "schemaHash", "a".repeat(64));
        Map<String, Object> targetPin = sameRecord
                ? sourcePin
                : Map.of(
                        "historyId", "target-history",
                        "entityId", targetEntityId,
                        "entityCode", targetEntityCode,
                        "version", 5,
                        "schemaHash", "b".repeat(64));
        Map<String, Object> config = Map.of(
                "enabled", enabled,
                "target", targetConfig,
                "presentation", Map.of("position", "INLINE"),
                "relation", relationConfig,
                "actions", List.of("VIEW"),
                "entitySnapshots", Map.of(
                        "source", sourcePin,
                        "target", targetPin),
                "specialHandling", Map.of(
                        "mode", "NONE",
                        "failurePolicy", "ERROR"));
        Map<String, Object> ownerSnapshot = Map.of(
                "form", Map.of(
                        "id", "owner-form",
                        "entityId", "source-entity-id"),
                "viewCompositions", List.of(Map.of(
                        "id", "composition-id",
                        "compositionKey", "related-content",
                        "config", config)));

        UiConfigRelease ownerRelease = release(
                "owner-release", "FORM", "owner-form", 4,
                "owner-content-hash");
        UiConfigRelease targetRelease = release(
                targetReleaseId,
                targetContentType,
                targetContentId,
                2,
                "target-content-hash");
        when(releaseMapper.selectById("owner-release"))
                .thenReturn(ownerRelease);
        when(releaseMapper.selectById(targetReleaseId))
                .thenReturn(targetRelease);
        when(releaseService.resolveRuntimeEventSnapshot(
                "owner-form", "owner-release", 4, null))
                .thenReturn(new UiConfigReleaseService.ResolvedUiEventSnapshot(
                        ownerSnapshot,
                        "owner-release",
                        4,
                        "owner-release",
                        false));

        Map<String, Object> targetSnapshot;
        if ("FORM".equals(targetContentType)) {
            EntityForm form = new EntityForm();
            form.setId(targetContentId);
            form.setEntityId(targetEntityId);
            form.setFormKey("target-form-key");
            form.setStatus(1);
            when(formMapper.selectById(targetContentId)).thenReturn(form);
            targetSnapshot = Map.of(
                    "form", Map.of(
                            "id", targetContentId,
                            "entityId", targetEntityId));
        } else {
            EntityListConfig list = new EntityListConfig();
            list.setId(targetContentId);
            list.setEntityId(targetEntityId);
            list.setListKey("target-list-key");
            when(listMapper.selectById(targetContentId)).thenReturn(list);
            targetSnapshot = Map.of(
                    "list", Map.of(
                            "id", targetContentId,
                            "entityId", targetEntityId,
                            "accessPermissionCode", "target:list"));
        }
        when(releaseService.verifiedReleaseSnapshot(targetRelease))
                .thenReturn(targetSnapshot);
        when(compositionTokenService.issueSourceRow(
                "FORM",
                "owner-form",
                "owner-release",
                4,
                "related-content",
                "source_entity",
                "source-record"))
                .thenReturn("row-context-token");
        if ("LIST".equals(targetContentType)) {
            when(compositionTokenService.advanceTraversal(
                    null,
                    "FORM",
                    "owner-form",
                    "owner-release",
                    4,
                    "related-content",
                    "source-record",
                    "LIST",
                    targetContentId,
                    targetReleaseId,
                    2,
                    null)).thenReturn("traversal-context-token");
            when(compositionTokenService.issueTargetList(
                    "FORM",
                    "owner-form",
                    "owner-release",
                    4,
                    "related-content",
                    "source_entity",
                    "source-record",
                    targetEntityCode,
                    targetContentId,
                    targetReleaseId,
                    2,
                    expectedFilters(relationType),
                    false)).thenReturn("list-context-token");
        }

        UiViewCompositionResolveRequest request =
                new UiViewCompositionResolveRequest();
        request.setOwnerType("FORM");
        request.setOwnerId("owner-form");
        request.setReleaseId("owner-release");
        request.setReleaseVersion(4);
        request.setCompositionKey("related-content");
        request.setRecordId("source-record");
        return new Fixture(
                request,
                expectedFilters(relationType),
                ownerRelease,
                ownerSnapshot);
    }

    private Map<String, Object> expectedFilters(String type) {
        return switch (type) {
            case "SAME_RECORD" -> Map.of(
                    "id", "source-record", "id_op", "EQ");
            case "REFERENCE_FIELD" -> Map.of(
                    "id", "target-record", "id_op", "EQ");
            case "FIELD_MATCH" -> Map.of(
                    "businessCode", "BIZ-001",
                    "businessCode_op", "EQ");
            default -> Map.of(
                    "projectId", "source-record",
                    "projectId_op", "EQ");
        };
    }

    private EntityDefinition entity(String id, String code) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id);
        entity.setEntityCode(code);
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        return entity;
    }

    private EntityDataDTO record(
            String id,
            Map<String, Object> data) {
        EntityDataDTO record = new EntityDataDTO();
        record.setId(id);
        record.setData(data);
        return record;
    }

    private EntityField field(
            String code,
            EntityField.FieldType type,
            String refEntityId) {
        EntityField field = new EntityField();
        field.setFieldCode(code);
        field.setFieldType(type);
        field.setRefEntityId(refEntityId);
        return field;
    }

    private EntityRelation relation() {
        EntityRelation relation = new EntityRelation();
        relation.setRelationCode("project-requirements");
        relation.setChildEntityId("target-entity-id");
        relation.setChildRefFieldCode("projectId");
        relation.setEnabled(true);
        return relation;
    }

    private EntityPublishedSnapshot snapshot(
            EntityDefinition entity,
            String historyId,
            int version,
            List<EntityField> fields,
            List<EntityRelation> relations) {
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setHistoryId(historyId);
        snapshot.setEntityId(entity.getId());
        snapshot.setEntityCode(entity.getEntityCode());
        snapshot.setVersion(version);
        snapshot.setFields(fields);
        snapshot.setRelations(relations);
        snapshot.setRelationsSnapshotAvailable(true);
        return snapshot;
    }

    private UiConfigRelease release(
            String id,
            String type,
            String configId,
            int version,
            String hash) {
        UiConfigRelease release = new UiConfigRelease();
        release.setId(id);
        release.setConfigType(type);
        release.setConfigId(configId);
        release.setVersion(version);
        release.setContentHash(hash);
        release.setStatus("ACTIVE");
        return release;
    }

    private record Fixture(
            UiViewCompositionResolveRequest request,
            Map<String, Object> expectedFilters,
            UiConfigRelease ownerRelease,
            Map<String, Object> ownerSnapshot) {
    }
}
