package com.workflow.service;

import com.workflow.entity.form.application.EntityFormNodeService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.ui.application.UiConfigDataSourceReferenceValidator;
import com.workflow.entity.ui.application.UiConfigSnapshotSupport;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiEventBindingSnapshotService;
import com.workflow.entity.ui.application.UiConfigSemanticPatchService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiExtensionDefinitionService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import com.workflow.entity.ui.application.UiViewCompositionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessImpact;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessImpactPort;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessTarget;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.contracts.audit.OperationContext;
import com.workflow.contracts.audit.OperationContextHolder;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.application.EntityListRelationalConfigService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.ui.api.response.UiConfigDiffDTO;
import com.workflow.entity.ui.api.response.UiConfigDraftDiscardResultDTO;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.request.UiConfigDraftDiscardRequest;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixTarget;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigReleaseAudit;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateVersionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigHotfixTargetMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseAuditMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.application.UiHotfixGovernanceService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UI 配置发布服务测试。
 *
 * <p>被测对象：{@link UiConfigReleaseService}，覆盖草稿与发布快照的差分比较、
 * 发布激活时的完整性校验、节点结构校验、跨表单嵌套校验、模板兼容性校验等场景。
 */
class UiConfigReleaseServiceTest {

    @Test
    void unchangedStandardFormPublishReusesRelease() {
        TestContext context = context();
        EntityForm draft = form();
        draft.setDataSourceBindingsDocument(null);
        when(context.formService().getById("form-1"))
                .thenReturn(draft);
        Map<String, Object> snapshot = context.service().draftSnapshot(
                UiConfigReleaseService.FORM,
                "form-1");
        UiConfigRelease active = configRelease(
                context.codec(),
                "form-release-active",
                UiConfigReleaseService.FORM,
                "form-1",
                snapshot);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        UiConfigRelease reused = context.service().publish(
                UiConfigReleaseService.FORM,
                "form-1",
                (String) null);

        assertEquals(active.getId(), reused.getId());
        verify(context.releaseMapper(), never()).insert(
                any(UiConfigRelease.class));
    }

    @Test
    void standardPublishPreviewValidatesEveryViewCompositionDependency() {
        TestContext context = context();
        EntityForm draft = form();
        draft.setDataSourceBindingsDocument(null);
        when(context.formService().getById("form-1"))
                .thenReturn(draft);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(null);

        UiConfigPublishRequest request = new UiConfigPublishRequest();
        request.setReleaseMode(UiConfigReleaseService.STANDARD);
        context.service().publishPreview(
                UiConfigReleaseService.FORM,
                "form-1",
                request);

        verify(context.viewCompositionService())
                .validateReleaseSnapshot(
                        org.mockito.ArgumentMatchers.eq(
                                UiConfigReleaseService.FORM),
                        org.mockito.ArgumentMatchers.eq("form-1"),
                        any());
    }

    @Test
    void standardPublishPreviewExplainsPinnedRelatedContentDependencies() {
        TestContext context = context();
        UiDataSourceDefinition serviceDefinition =
                new UiDataSourceDefinition();
        serviceDefinition.setId("service-1");
        serviceDefinition.setEnabled(true);
        serviceDefinition.setDeleted(0);
        serviceDefinition.setScopeType("GLOBAL");
        serviceDefinition.setOperationsDocument("""
                [{"code":"query","contextType":"FORM","kind":"READ"}]
                """);
        when(context.dataSourceDefinitionMapper().selectById("service-1"))
                .thenReturn(serviceDefinition);
        EntityForm draft = form();
        draft.setDataSourceBindingsDocument(null);
        when(context.formService().getById("form-1"))
                .thenReturn(draft);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(null);
        when(context.viewCompositionService().snapshot(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(List.of(Map.of(
                        "id", "composition-1",
                        "compositionKey", "projectRequirements",
                        "anchorType", "OWNER",
                        "anchorKey", "",
                        "orderKey", 1000L,
                        "config", Map.of(
                                "name", "项目需求",
                                "source", Map.of(
                                        "entityId", "entity-project",
                                        "entityCode", "project",
                                        "entityName", "项目"),
                                "target", Map.of(
                                        "entityId", "entity-requirement",
                                        "entityCode", "requirement",
                                        "entityName", "需求",
                                        "contentType", "LIST",
                                        "contentId", "list-target",
                                        "contentKey", "requirement-list",
                                        "contentName", "需求列表",
                                        "releaseId", "release-list-7",
                                        "releaseVersion", 7),
                                "entitySnapshots", Map.of(
                                        "source", Map.of(
                                                "historyId", "entity-history-project-4",
                                                "entityId", "entity-project",
                                                "entityCode", "project",
                                                "version", 4,
                                                "schemaHash", "a".repeat(64)),
                                        "target", Map.of(
                                                "historyId", "entity-history-requirement-6",
                                                "entityId", "entity-requirement",
                                                "entityCode", "requirement",
                                                "version", 6,
                                                "schemaHash", "b".repeat(64))),
                                "specialHandling", Map.of(
                                        "interfaceService", Map.of(
                                                "serviceId", "service-1",
                                                "serviceName", "需求聚合服务",
                                                "sourceCode", "REQ_AGG",
                                                "operationCode", "query",
                                                "serviceRevision", 3),
                                        "customComponent", Map.of(
                                                "name", "requirement-board",
                                                "displayName", "需求看板",
                                                "version", 2))))));

        UiConfigPublishRequest request = new UiConfigPublishRequest();
        request.setReleaseMode(UiConfigReleaseService.STANDARD);
        UiConfigPublishPreviewDTO preview = context.service().publishPreview(
                UiConfigReleaseService.FORM,
                "form-1",
                request);

        assertEquals(5, preview.getDependencies().size());
        assertTrue(preview.getDependencies().stream().anyMatch(item ->
                "ENTITY_SCHEMA".equals(item.get("type"))
                        && "project".equals(item.get("key"))
                        && Integer.valueOf(4).equals(item.get("version"))));
        assertTrue(preview.getDependencies().stream().anyMatch(item ->
                "ENTITY_SCHEMA".equals(item.get("type"))
                        && "requirement".equals(item.get("key"))
                        && Integer.valueOf(6).equals(item.get("version"))));
        assertTrue(preview.getDependencies().stream().anyMatch(item ->
                "LIST".equals(item.get("type"))
                        && Integer.valueOf(7).equals(item.get("version"))));
        assertTrue(preview.getDependencies().stream().anyMatch(item ->
                "INTERFACE_SERVICE".equals(item.get("type"))
                        && Integer.valueOf(3).equals(item.get("version"))));
        assertTrue(preview.getDependencies().stream().anyMatch(item ->
                "CUSTOM_COMPONENT".equals(item.get("type"))
                        && Integer.valueOf(2).equals(item.get("version"))));
    }

    @Test
    void activationValidatesViewCompositionsBeforeSwitchingActiveRelease() {
        TestContext context = context();
        UiConfigRelease target = release(
                context.codec(),
                "release-target",
                formSnapshot(List.of()));
        target.setPublishedBy("admin-1");
        when(context.releaseMapper().selectById("release-target"))
                .thenReturn(target);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(null);
        when(context.releaseMapper().update(any(), any()))
                .thenReturn(1);
        context.service().activate(
                UiConfigReleaseService.FORM,
                "form-1",
                "release-target");

        verify(context.viewCompositionService())
                .validateReleaseSnapshot(
                        org.mockito.ArgumentMatchers.eq(
                                UiConfigReleaseService.FORM),
                        org.mockito.ArgumentMatchers.eq("form-1"),
                        any());
    }

    @Test
    void invalidViewCompositionStopsActivationBeforeAnyReleaseStateChange() {
        TestContext context = context();
        UiConfigRelease target = release(
                context.codec(),
                "release-invalid-composition",
                formSnapshot(List.of()));
        when(context.releaseMapper().selectById(
                "release-invalid-composition"))
                .thenReturn(target);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(null);
        org.mockito.Mockito.doThrow(new IllegalArgumentException(
                        "关联内容“project_requirements”挂载的表单节点不存在"))
                .when(context.viewCompositionService())
                .validateReleaseSnapshot(
                        org.mockito.ArgumentMatchers.eq(
                                UiConfigReleaseService.FORM),
                        org.mockito.ArgumentMatchers.eq("form-1"),
                        any());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        "release-invalid-composition"));

        assertTrue(failure.getMessage().contains("挂载的表单节点不存在"));
        verify(context.releaseMapper(), never()).update(any(), any());
    }

    @Test
    void hotfixEffectiveSnapshotUsesTheSameViewCompositionValidation() {
        TestContext context = context();
        Map<String, Object> effective = formSnapshot(List.of());

        ReflectionTestUtils.invokeMethod(
                context.service(),
                "validatedEffectiveSnapshot",
                UiConfigReleaseService.FORM,
                "form-1",
                effective);

        verify(context.viewCompositionService())
                .validateReleaseSnapshot(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        effective);
    }

    @Test
    void missingViewCompositionServiceFailsSnapshotAndRestoreClosed() {
        TestContext context = context();
        when(context.formService().getById("form-1"))
                .thenReturn(form());
        ReflectionTestUtils.setField(
                context.service(),
                "viewCompositionService",
                null);

        IllegalStateException snapshotFailure = assertThrows(
                IllegalStateException.class,
                () -> context.service().draftSnapshot(
                        UiConfigReleaseService.FORM,
                        "form-1"));
        IllegalStateException restoreFailure = assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        context.service(),
                        "restoreViewCompositions",
                        UiConfigReleaseService.FORM,
                        "form-1",
                        List.of()));

        assertTrue(snapshotFailure.getMessage().contains("关联内容服务未装配"));
        assertTrue(restoreFailure.getMessage().contains("关联内容服务未装配"));
    }

    @Test
    void formDiscardDraftRestoresActiveSnapshotAndAlignsHash() {
        TestContext context = context();
        EntityForm published = form();
        published.setFormName("已发布表单");
        published.setRevision(10);
        published.setActiveReleaseId("release-active");
        when(context.formService().getById("form-1"))
                .thenReturn(published);
        Map<String, Object> activeSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.FORM, "form-1");
        UiConfigRelease active = configRelease(
                context.codec(),
                "release-active",
                UiConfigReleaseService.FORM,
                "form-1",
                activeSnapshot);

        EntityForm current = form();
        current.setFormName("未发布表单");
        current.setRevision(9);
        current.setActiveReleaseId(active.getId());
        when(context.formService().getById("form-1"))
                .thenReturn(current);
        Map<String, Object> currentSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.FORM, "form-1");
        String currentHash = snapshotHash(
                context.codec(), currentSnapshot);

        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        when(context.formService().getById("form-1"))
                .thenReturn(current, published);
        when(context.formService().restoreFormForRelease(
                any(EntityForm.class),
                org.mockito.ArgumentMatchers.eq(9)))
                .thenReturn(published);

        UiConfigDraftDiscardResultDTO result = context.service()
                .discardDraft(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        discardRequest(
                                9,
                                currentHash,
                                active.getId()));

        assertEquals(9, result.getPreviousRevision());
        assertEquals(10, result.getRevision());
        assertEquals(active.getContentHash(), result.getDraftHash());
        assertEquals(active.getContentHash(), result.getPublishedHash());
        verify(context.formService()).restoreFormForRelease(
                any(EntityForm.class),
                org.mockito.ArgumentMatchers.eq(9));
        verify(context.releaseMapper(), never())
                .insert(any(UiConfigRelease.class));
    }

    @Test
    void formDiscardNormalizesLegacyActiveSnapshotLikeDiff() {
        TestContext context = context();
        EntityForm published = form();
        published.setFormName("已发布表单");
        published.setRevision(10);
        published.setActiveReleaseId("release-active");
        when(context.formService().getById("form-1"))
                .thenReturn(published);
        Map<String, Object> stableActiveSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.FORM, "form-1");
        Map<String, Object> legacyActiveSnapshot = new LinkedHashMap<>(
                stableActiveSnapshot);
        legacyActiveSnapshot.put("revision", 7);
        UiConfigRelease active = configRelease(
                context.codec(),
                "release-active",
                UiConfigReleaseService.FORM,
                "form-1",
                legacyActiveSnapshot);

        EntityForm current = form();
        current.setFormName("未发布表单");
        current.setRevision(9);
        current.setActiveReleaseId(active.getId());
        when(context.formService().getById("form-1"))
                .thenReturn(current);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        Map<String, Object> currentSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.FORM, "form-1");
        String currentHash = snapshotHash(
                context.codec(), currentSnapshot);

        UiConfigDiffDTO diff = context.service().diff(
                UiConfigReleaseService.FORM,
                "form-1");
        assertTrue(diff.isCanDiscardDraft());
        assertFalse(diff.isDependencyChanged());

        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);
        when(context.formService().getById("form-1"))
                .thenReturn(current, published);
        when(context.formService().restoreFormForRelease(
                any(EntityForm.class),
                org.mockito.ArgumentMatchers.eq(9)))
                .thenReturn(published);

        UiConfigDraftDiscardResultDTO result = context.service()
                .discardDraft(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        discardRequest(
                                9,
                                currentHash,
                                active.getId()));

        assertEquals(
                snapshotHash(context.codec(), stableActiveSnapshot),
                result.getDraftHash());
        assertFalse(result.isRemainingChanged());
        assertFalse(result.isDependencyChanged());
    }

    @Test
    void listDiscardDraftRestoresActiveSnapshotAndAlignsHash() {
        TestContext context = context();
        EntityListConfigDTO published = listConfig(3);
        published.setListName("已发布列表");
        published.setRevision(6);
        published.setActiveReleaseId("list-release-active");
        when(context.listConfigService().findById("list-1"))
                .thenReturn(published);
        Map<String, Object> activeSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.LIST, "list-1");
        UiConfigRelease active = configRelease(
                context.codec(),
                "list-release-active",
                UiConfigReleaseService.LIST,
                "list-1",
                activeSnapshot);

        EntityListConfigDTO current = listConfig(3);
        current.setListName("未发布列表");
        current.setRevision(5);
        current.setActiveReleaseId(active.getId());
        when(context.listConfigService().findById("list-1"))
                .thenReturn(current);
        Map<String, Object> currentSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.LIST, "list-1");
        String currentHash = snapshotHash(
                context.codec(), currentSnapshot);

        EntityListConfig owner = new EntityListConfig();
        owner.setId("list-1");
        owner.setEntityId("entity-1");
        owner.setRevision(5);
        owner.setActiveReleaseId(active.getId());
        when(context.listConfigMapper().selectByIdForUpdate("list-1"))
                .thenReturn(owner);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.LIST,
                "list-1")).thenReturn(active);
        when(context.listConfigService().findById("list-1"))
                .thenReturn(current, published);
        when(context.listConfigService().restoreConfigForRelease(
                any(EntityListConfigDTO.class),
                org.mockito.ArgumentMatchers.eq(5)))
                .thenReturn(published);

        UiConfigDraftDiscardResultDTO result = context.service()
                .discardDraft(
                        UiConfigReleaseService.LIST,
                        "list-1",
                        discardRequest(
                                5,
                                currentHash,
                                active.getId()));

        assertEquals(6, result.getRevision());
        assertEquals(active.getContentHash(), result.getDraftHash());
        verify(context.listConfigService()).restoreConfigForRelease(
                any(EntityListConfigDTO.class),
                org.mockito.ArgumentMatchers.eq(5));
        verify(context.releaseMapper(), never())
                .insert(any(UiConfigRelease.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void listDiscardNormalizesLegacyButtonDefaultsWithoutKeepingLocalChanges() {
        TestContext context = context();
        EntityListConfigDTO published = listConfig(3);
        published.setListName("已发布列表");
        published.setRevision(6);
        published.setActiveReleaseId("list-release-active");
        when(context.listConfigService().findById("list-1"))
                .thenReturn(published);
        Map<String, Object> legacySnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.LIST, "list-1");
        Map<String, Object> publishedList =
                (Map<String, Object>) legacySnapshot.get("list");
        Map<String, Object> legacyCreate =
                (Map<String, Object>) ((List<?>) publishedList.get(
                        "toolbarConfig")).get(0);
        for (String key : List.of(
                "id", "orderKey", "link", "sort", "type", "enabled")) {
            legacyCreate.remove(key);
        }
        UiConfigRelease active = configRelease(
                context.codec(),
                "list-release-active",
                UiConfigReleaseService.LIST,
                "list-1",
                legacySnapshot);

        EntityListConfigDTO current = listConfig(3);
        current.setListName("未发布列表");
        current.setRevision(5);
        current.setActiveReleaseId(active.getId());
        Map<String, Object> currentCreate =
                current.getToolbarConfig().get(0);
        currentCreate.put("link", true);
        currentCreate.put("sort", 9);
        currentCreate.put("orderKey", 9_000_000L);
        java.util.concurrent.atomic.AtomicReference<EntityListConfigDTO>
                restoredRef = new java.util.concurrent.atomic.AtomicReference<>();
        when(context.listConfigService().findById("list-1"))
                .thenAnswer(invocation -> restoredRef.get() == null
                        ? current : restoredRef.get());
        Map<String, Object> currentSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.LIST, "list-1");
        String currentHash = snapshotHash(
                context.codec(), currentSnapshot);

        EntityListConfig owner = new EntityListConfig();
        owner.setId("list-1");
        owner.setEntityId("entity-1");
        owner.setRevision(5);
        owner.setActiveReleaseId(active.getId());
        when(context.listConfigMapper().selectByIdForUpdate("list-1"))
                .thenReturn(owner);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.LIST,
                "list-1")).thenReturn(active);
        when(context.listConfigService().restoreConfigForRelease(
                any(EntityListConfigDTO.class),
                org.mockito.ArgumentMatchers.eq(5)))
                .thenAnswer(invocation -> {
                    EntityListConfigDTO restored = invocation.getArgument(0);
                    restored.setRevision(6);
                    restored.setActiveReleaseId(active.getId());
                    restoredRef.set(restored);
                    return restored;
                });

        UiConfigDiffDTO diff = context.service().diff(
                UiConfigReleaseService.LIST,
                "list-1");
        assertTrue(diff.isCanDiscardDraft());
        assertFalse(diff.isDependencyChanged());

        UiConfigDraftDiscardResultDTO result = context.service()
                .discardDraft(
                        UiConfigReleaseService.LIST,
                        "list-1",
                        discardRequest(
                                5,
                                currentHash,
                                active.getId()));

        ArgumentCaptor<EntityListConfigDTO> restoredCaptor =
                ArgumentCaptor.forClass(EntityListConfigDTO.class);
        verify(context.listConfigService()).restoreConfigForRelease(
                restoredCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(5));
        Map<String, Object> restoredCreate = restoredCaptor.getValue()
                .getToolbarConfig().get(0);
        assertEquals("toolbar-create", restoredCreate.get("id"));
        assertEquals(1_000_000L, restoredCreate.get("orderKey"));
        assertEquals(0, restoredCreate.get("sort"));
        assertEquals("built-in", restoredCreate.get("type"));
        assertEquals(false, restoredCreate.get("link"));
        assertEquals(true, restoredCreate.get("enabled"));
        assertFalse(result.isRemainingChanged());
        assertFalse(result.isDependencyChanged());
    }

    @Test
    void diffDoesNotOfferDiscardForPureInheritedBindingDrift() {
        TestContext context = context();
        EntityForm form = form();
        form.setActiveReleaseId("release-active");
        when(context.formService().getById("form-1"))
                .thenReturn(form);
        UiEventBinding inherited = inheritedBinding("[]");
        when(context.eventBindingMapper().findForSnapshot(
                UiConfigReleaseService.FORM,
                "form-1",
                "entity-1"))
                .thenReturn(List.of(inherited));
        Map<String, Object> activeSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.FORM, "form-1");
        UiConfigRelease active = configRelease(
                context.codec(),
                "release-active",
                UiConfigReleaseService.FORM,
                "form-1",
                activeSnapshot);
        inherited.setStepsDocument(
                "[{\"stepCode\":\"EXTERNAL_CHANGE\",\"strategy\":\"AFTER\"}]");
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);

        UiConfigDiffDTO diff = context.service().diff(
                UiConfigReleaseService.FORM,
                "form-1");

        assertTrue(diff.isChanged());
        assertFalse(diff.isDiscardableChanged());
        assertFalse(diff.isCanDiscardDraft());
        assertTrue(diff.getDiscardBlockedReason().contains("继承"));
    }

    @Test
    void formDiscardKeepsInheritedDriftAsRemainingDifference() {
        TestContext context = context();
        EntityForm published = form();
        published.setFormName("已发布表单");
        published.setRevision(10);
        published.setActiveReleaseId("release-active");
        when(context.formService().getById("form-1"))
                .thenReturn(published);
        UiEventBinding inherited = inheritedBinding("[]");
        when(context.eventBindingMapper().findForSnapshot(
                UiConfigReleaseService.FORM,
                "form-1",
                "entity-1"))
                .thenReturn(List.of(inherited));
        Map<String, Object> activeSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.FORM, "form-1");
        UiConfigRelease active = configRelease(
                context.codec(),
                "release-active",
                UiConfigReleaseService.FORM,
                "form-1",
                activeSnapshot);

        EntityForm current = form();
        current.setFormName("本地未发布表单");
        current.setRevision(9);
        current.setActiveReleaseId(active.getId());
        inherited.setStepsDocument(
                "[{\"stepCode\":\"ENTITY_DEFAULT_CHANGED\",\"strategy\":\"AFTER\"}]");
        when(context.formService().getById("form-1"))
                .thenReturn(current);
        Map<String, Object> currentSnapshot = context.service()
                .draftSnapshot(UiConfigReleaseService.FORM, "form-1");
        String currentHash = snapshotHash(
                context.codec(), currentSnapshot);

        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        when(context.formService().getById("form-1"))
                .thenReturn(current, published);
        when(context.formService().restoreFormForRelease(
                any(EntityForm.class),
                org.mockito.ArgumentMatchers.eq(9)))
                .thenReturn(published);

        UiConfigDraftDiscardResultDTO result = context.service()
                .discardDraft(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        discardRequest(
                                9,
                                currentHash,
                                active.getId()));

        assertTrue(result.isRemainingChanged());
        assertTrue(result.isDependencyChanged());
        assertFalse(result.getDraftHash().equals(
                result.getPublishedHash()));
    }

    @Test
    void discardDraftRejectsConfigurationWithoutPublishedBaseline() {
        TestContext context = context();
        EntityForm owner = form();
        owner.setActiveReleaseId(null);
        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(owner);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(null);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().discardDraft(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        discardRequest(9, "draft-hash", "release-missing")));

        assertEquals(
                "UI_CONFIG_ACTIVE_RELEASE_REQUIRED",
                exception.getErrorCode());
    }

    @Test
    void eventBindingReleaseRestoreReusesPublishedStableId() {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        mapper,
                        mock(UiDataSourceDefinitionMapper.class),
                        codec);
        when(mapper.findByOwnerForUpdate("FORM", "form-1"))
                .thenReturn(List.of());

        service.restoreLocalBindingsForRelease(
                "FORM",
                "form-1",
                List.of(Map.of(
                        "id", "published-binding-id",
                        "ownerType", "FORM",
                        "ownerId", "form-1",
                        "targetType", "OWNER",
                        "targetKey", "",
                        "eventCode", "FORM_OPEN",
                        "inheritanceMode", "INHERIT",
                        "steps", List.of())));

        ArgumentCaptor<UiEventBinding> captor =
                ArgumentCaptor.forClass(UiEventBinding.class);
        verify(mapper).deleteByOwner("FORM", "form-1");
        verify(mapper).insert(captor.capture());
        assertEquals(
                "published-binding-id",
                captor.getValue().getId());
        assertEquals(1, captor.getValue().getRevision());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listDraftSnapshotPinsExplicitTargetFormRelease() {
        TestContext context = context();
        EntityListConfigDTO list = new EntityListConfigDTO();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setToolbarConfig(List.of(Map.of(
                "key", "create",
                "type", "built-in",
                "targetFormId", "form-2")));
        list.setRowActionConfig(List.of());
        when(context.listConfigService().findById("list-1"))
                .thenReturn(list);

        EntityForm targetForm = new EntityForm();
        targetForm.setId("form-2");
        targetForm.setEntityId("entity-1");
        targetForm.setStatus(1);
        targetForm.setActiveReleaseId("form-release-3");
        when(context.formMapper().selectById("form-2"))
                .thenReturn(targetForm);
        UiConfigRelease formRelease = new UiConfigRelease();
        formRelease.setId("form-release-3");
        formRelease.setConfigType(UiConfigReleaseService.FORM);
        formRelease.setConfigId("form-2");
        formRelease.setVersion(3);
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-2")).thenReturn(formRelease);

        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(
                context.service(),
                "buildDraftSnapshot",
                UiConfigReleaseService.LIST,
                "list-1");
        Map<String, Object> snapshotList =
                (Map<String, Object>) snapshot.get("list");
        Map<String, Object> button =
                (Map<String, Object>) ((List<?>) snapshotList.get(
                        "toolbarConfig")).get(0);

        assertEquals("form-release-3",
                button.get("targetFormReleaseId"));
        assertEquals(3, button.get("targetFormReleaseVersion"));
    }

    /**
     * 测试与历史发布比较时忽略草稿修订号与时间戳字段：
     * 验证 revision/activeReleaseId/updatedAt/updateTime 不一致时仍判定为未变更。
     */
    @Test
    void ignoresDraftRevisionsAndTimestampsWhenComparingLegacyRelease() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityForm form = form();
        when(formService.getById("form-1")).thenReturn(form);

        UiConfigReleaseService service = new UiConfigReleaseService(
                releaseMapper,
                mock(UiConfigHotfixTargetMapper.class),
                mock(UiConfigReleaseAuditMapper.class),
                new UiConfigDataSourceReferenceValidator(
                        mock(UiDataSourceDefinitionMapper.class),
                        codec),
                new UiEventBindingSnapshotService(
                        mock(UiEventBindingMapper.class),
                        mock(UiDataSourceDefinitionMapper.class),
                        codec),
                new UiConfigSnapshotSupport(codec, objectMapper),
                mock(UiComponentTemplateMapper.class),
                mock(UiComponentTemplateVersionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionMapper.class),
                formService,
                mock(EntityFormNodeService.class),
                mock(EntityFormConfigurationValidator.class),
                mock(UiExtensionDefinitionService.class),
                mock(EntityListConfigService.class),
                mock(EntityListConfigurationValidator.class),
                new UiConfigSemanticPatchService(codec),
                mock(UiHotfixProcessImpactPort.class),
                mock(UiConfigurationAccessService.class),
                mock(UiReleaseResolutionTokenService.class),
                mock(FormSubmissionTraceService.class),
                codec,
                objectMapper,
                mock(MigrationAssetHandler.class));
        attachViewCompositionService(service);

        Map<String, Object> legacySnapshot = objectMapper.convertValue(
                service.draftSnapshot(UiConfigReleaseService.FORM, "form-1"),
                Map.class);
        ((Map<String, Object>) legacySnapshot.get("form"))
                .put("revision", 1);
        ((Map<String, Object>) legacySnapshot.get("form"))
                .put("activeReleaseId", null);
        ((Map<String, Object>) ((List<?>) legacySnapshot.get("nodes")).get(0))
                .put("revision", 1);
        ((Map<String, Object>) ((List<?>) legacySnapshot.get("nodes")).get(0))
                .put("updatedAt", "2026-01-01T00:00:00");
        ((Map<String, Object>) ((List<?>) legacySnapshot.get("legacyFields")).get(0))
                .put("updateTime", "2026-01-01T00:00:00");

        String releaseDocument = codec.canonicalize(
                codec.write(legacySnapshot, "测试历史发布快照"),
                "测试历史发布快照");
        UiConfigRelease release = new UiConfigRelease();
        release.setSnapshotDocument(releaseDocument);
        release.setContentHash(sha256(releaseDocument));
        when(releaseMapper.findActive(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(release);

        UiConfigDiffDTO diff = service.diff(
                UiConfigReleaseService.FORM, "form-1");
        Map<String, Object> draftField = (Map<String, Object>) (
                (List<?>) service.draftSnapshot(
                        UiConfigReleaseService.FORM, "form-1")
                        .get("legacyFields")).get(0);
        Map<String, Object> draftForm = (Map<String, Object>)
                service.draftSnapshot(
                        UiConfigReleaseService.FORM,
                        "form-1").get("form");

        assertFalse(diff.isChanged(), diff.toString());
        assertTrue(diff.getChangedSections().isEmpty());
        assertEquals("实体字段名称", draftField.get("fieldName"));
        assertEquals("STRING", draftField.get("fieldType"));
        assertEquals("{}", draftField.get("componentProps"));
        assertEquals(
                "{\"FORM_INIT\":{\"serviceId\":\"source-init\","
                        + "\"operationCode\":\"initializeForm\"}}",
                draftForm.get("dataSourceBindingsDocument"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ignoresMissingVersusEmptyComponentPropsInDiff() {
        TestContext context = context();
        EntityForm form = form();
        form.setFields(List.of());
        when(context.formService().getById("form-1")).thenReturn(form);

        Map<String, Object> activeSnapshot = context.codec().readObject(
                context.codec().write(
                        context.service().draftSnapshot(
                                UiConfigReleaseService.FORM,
                                "form-1"),
                        "测试空组件属性快照"),
                "测试空组件属性快照");
        Map<String, Object> activeNode =
                (Map<String, Object>) ((List<?>) activeSnapshot.get(
                        "nodes")).get(0);
        Map<String, Object> activeProps = context.codec().readObject(
                String.valueOf(activeNode.get("propsDocument")),
                "测试节点属性");
        activeProps.put("componentProps", new LinkedHashMap<>());
        activeNode.put(
                "propsDocument",
                context.codec().write(activeProps, "测试节点属性"));
        Map<String, Object> activeField =
                (Map<String, Object>) ((List<?>) activeSnapshot.get(
                        "legacyFields")).get(0);
        activeField.put("componentProps", "{}");

        String releaseDocument = context.codec().canonicalize(
                context.codec().write(
                        activeSnapshot,
                        "测试历史发布快照"),
                "测试历史发布快照");
        UiConfigRelease release = new UiConfigRelease();
        release.setSnapshotDocument(releaseDocument);
        release.setContentHash(sha256(releaseDocument));
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(release);

        UiConfigDiffDTO diff = context.service().diff(
                UiConfigReleaseService.FORM,
                "form-1");

        assertFalse(diff.isChanged(), diff.toString());
        assertTrue(diff.getChangedSections().isEmpty());
        assertTrue(diff.getChangedItems().isEmpty());
    }

    /**
     * 测试在详细差分中报告节点的稳定移动（MOVED）：
     * 验证仅 parentId/orderKey 变化时被识别为 MOVED 且变更字段集合包含这两个字段。
     */
    @Test
    @SuppressWarnings("unchecked")
    void reportsStableNodeMoveInDetailedDiff() {
        TestContext context = context();
        EntityForm form = form();
        when(context.formService().getById("form-1")).thenReturn(form);
        Map<String, Object> activeSnapshot = new LinkedHashMap<>(
                context.service().draftSnapshot(
                        UiConfigReleaseService.FORM, "form-1"));
        List<Map<String, Object>> activeNodes = new ArrayList<>(
                (List<Map<String, Object>>) activeSnapshot.get("nodes"));
        Map<String, Object> movedNode = new LinkedHashMap<>(activeNodes.get(0));
        movedNode.put("parentId", "section-1");
        movedNode.put("orderKey", 100L);
        activeNodes.set(0, movedNode);
        activeSnapshot.put("nodes", activeNodes);

        String releaseDocument = context.codec().canonicalize(
                context.codec().write(
                        activeSnapshot,
                        "测试移动节点发布快照"),
                "测试移动节点发布快照");
        UiConfigRelease active = new UiConfigRelease();
        active.setSnapshotDocument(releaseDocument);
        active.setContentHash(sha256(releaseDocument));
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(active);

        UiConfigDiffDTO diff = context.service().diff(
                UiConfigReleaseService.FORM, "form-1");

        assertTrue(diff.getChangedItems().stream().anyMatch(item ->
                "nodes".equals(item.getSection())
                        && "node-1".equals(item.getId())
                        && "MOVED".equals(item.getChangeType())
                        && item.getChangedFields().contains("parentId")
                        && item.getChangedFields().contains("orderKey")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void formStructuralHotfixIsReviewAndPublishableWithoutOverride() {
        TestContext context = context();
        EntityForm form = form();
        form.setDataSourceBindingsDocument("{}");
        when(context.formService().getById("form-1")).thenReturn(form);

        Map<String, Object> activeSnapshot =
                context.codec().readObject(
                        context.codec().write(
                                context.service().draftSnapshot(
                                        UiConfigReleaseService.FORM,
                                        "form-1"),
                                "测试流程表单热修复基线"),
                        "测试流程表单热修复基线");
        activeSnapshot.put("nodes", List.of());
        UiConfigRelease active = release(
                context.codec(),
                "release-1",
                activeSnapshot);
        active.setStatus("ACTIVE");
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        when(context.processImpactPort().analyzeFormImpact("form-1"))
                .thenReturn(UiHotfixProcessImpact.empty());

        UiConfigPublishRequest request = new UiConfigPublishRequest();
        request.setReleaseMode(UiConfigReleaseService.HOTFIX);

        UiConfigPublishPreviewDTO preview =
                context.service().publishPreview(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        request);

        assertEquals(
                UiConfigSemanticPatchService.REVIEW,
                preview.getRiskLevel());
        assertFalse(preview.isRequiresOverride());
        assertTrue(preview.isCanPublish());
        assertTrue(preview.getBlockers().isEmpty());
        assertTrue(preview.getRiskItems().stream().noneMatch(item ->
                UiConfigSemanticPatchService.BLOCKED.equals(
                        item.getRiskLevel())));
    }

    @Test
    void rejectsListHotfixPreview() {
        TestContext context = context();
        EntityListConfigDTO list = listConfig(5);
        when(context.listConfigService().findById("list-1"))
                .thenReturn(list);

        Map<String, Object> activeSnapshot =
                context.codec().readObject(
                        context.codec().write(
                                context.service().draftSnapshot(
                                        UiConfigReleaseService.LIST,
                                        "list-1"),
                                "测试列表热修复基线"),
                        "测试列表热修复基线");
        Map<String, Object> activeList =
                (Map<String, Object>) activeSnapshot.get("list");
        Map<String, Object> activeViewConfig =
                (Map<String, Object>) activeList.get("viewConfig");
        Map<String, Object> activeSearch =
                (Map<String, Object>) activeViewConfig.get("search");
        activeSearch.put("defaultVisibleCount", 3);
        UiConfigRelease active = release(
                context.codec(),
                "release-list-1",
                activeSnapshot);
        active.setConfigType(UiConfigReleaseService.LIST);
        active.setConfigId("list-1");
        active.setStatus("ACTIVE");
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.LIST,
                "list-1")).thenReturn(active);

        UiConfigPublishRequest request = new UiConfigPublishRequest();
        request.setReleaseMode(UiConfigReleaseService.HOTFIX);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().publishPreview(
                        UiConfigReleaseService.LIST,
                        "list-1",
                        request));

        assertEquals(
                "LIST_HOTFIX_NOT_SUPPORTED",
                exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void corruptPreviousHotfixFallsBackToValidatedFullSnapshot() {
        TestContext context = context();
        EntityForm draft = form();
        when(context.formService().getById("form-1"))
                .thenReturn(draft);

        Map<String, Object> activeSnapshot =
                context.codec().readObject(
                        context.codec().write(
                                context.service().draftSnapshot(
                                        UiConfigReleaseService.FORM,
                                        "form-1"),
                                "测试强制热修复当前基线"),
                        "测试强制热修复当前基线");
        Map<String, Object> activeNode =
                (Map<String, Object>) ((List<?>) activeSnapshot.get(
                        "nodes")).get(0);
        Map<String, Object> activeProps =
                context.codec().readObject(
                        String.valueOf(activeNode.get("propsDocument")),
                        "测试强制热修复当前基线节点");
        activeProps.put("label", "发布前标题");
        activeNode.put(
                "propsDocument",
                context.codec().write(
                        activeProps,
                        "测试强制热修复当前基线节点"));
        UiConfigRelease active = release(
                context.codec(),
                "release-2",
                activeSnapshot);
        active.setVersion(2);
        active.setStatus("ACTIVE");

        UiConfigRelease pinned = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(labelNode("原始标题"))));
        UiConfigHotfixTarget previous = target(
                context.codec(),
                "target-broken",
                "hotfix-2",
                "release-1",
                1,
                formSnapshot(List.of(labelNode("旧热修复标题"))));
        previous.setEffectiveContentHash("tampered");

        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        when(context.releaseMapper().findReleases(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(List.of(active));
        when(context.releaseMapper().selectById("release-1"))
                .thenReturn(pinned);
        when(context.hotfixTargetMapper().findActiveTarget(
                UiConfigReleaseService.FORM,
                "form-1",
                "history-1")).thenReturn(previous);
        when(context.hotfixTargetMapper().update(any(), any()))
                .thenReturn(1);
        when(context.processImpactPort().analyzeFormImpact(
                "form-1")).thenReturn(new UiHotfixProcessImpact(
                        List.of(processTarget(
                                "release-1",
                                1)),
                        1,
                        1L,
                        0L,
                        "impact-force-1"));

        AtomicReference<UiConfigHotfixTarget> savedTarget =
                new AtomicReference<>();
        doAnswer(invocation -> {
            UiConfigRelease release = invocation.getArgument(0);
            release.setId("hotfix-3");
            return 1;
        }).when(context.releaseMapper()).insert(
                any(UiConfigRelease.class));
        doAnswer(invocation -> {
            UiConfigHotfixTarget target = invocation.getArgument(0);
            target.setId("target-force-1");
            savedTarget.set(target);
            return 1;
        }).when(context.hotfixTargetMapper()).insert(
                any(UiConfigHotfixTarget.class));

        UiConfigPublishRequest previewRequest =
                new UiConfigPublishRequest();
        previewRequest.setReleaseMode(
                UiConfigReleaseService.HOTFIX);
        UiConfigPublishPreviewDTO preview =
                context.service().publishPreview(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        previewRequest);

        assertTrue(preview.isCanPublish());
        assertTrue(preview.getBlockers().isEmpty());
        assertEquals(
                UiConfigSemanticPatchService.REVIEW,
                preview.getRiskLevel());
        assertEquals(
                "FULL_SNAPSHOT",
                preview.getTargets().get(0).getApplicationMode());
        assertTrue(preview.getTargets().get(0)
                .getReviewNotes().stream().anyMatch(note ->
                        note.contains("完整性校验失败")));

        context.service().publish(
                UiConfigReleaseService.FORM,
                "form-1",
                hotfixPublishRequest(preview));

        assertNull(savedTarget.get().getPreviousTargetId());
        Map<String, Object> effective =
                context.codec().readObject(
                        savedTarget.get()
                                .getEffectiveSnapshotDocument(),
                        "测试强制热修复有效快照");
        Map<String, Object> effectiveNode =
                (Map<String, Object>) ((List<?>) effective.get(
                        "nodes")).get(0);
        assertEquals(
                "名称",
                context.codec().readObject(
                        String.valueOf(
                                effectiveNode.get("propsDocument")),
                        "测试强制热修复有效快照节点").get("label"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingStableTargetEntryUsesFullSnapshotFallback() {
        TestContext context = context();
        EntityForm draft = form();
        when(context.formService().getById("form-1"))
                .thenReturn(draft);

        Map<String, Object> activeSnapshot =
                context.codec().readObject(
                        context.codec().write(
                                context.service().draftSnapshot(
                                        UiConfigReleaseService.FORM,
                                        "form-1"),
                                "测试旧流程稳定条目基线"),
                        "测试旧流程稳定条目基线");
        Map<String, Object> activeNode =
                (Map<String, Object>) ((List<?>) activeSnapshot.get(
                        "nodes")).get(0);
        Map<String, Object> activeProps =
                context.codec().readObject(
                        String.valueOf(activeNode.get("propsDocument")),
                        "测试旧流程稳定条目基线节点");
        activeProps.put("label", "发布前标题");
        activeNode.put(
                "propsDocument",
                context.codec().write(
                        activeProps,
                        "测试旧流程稳定条目基线节点"));
        UiConfigRelease active = release(
                context.codec(),
                "release-2",
                activeSnapshot);
        active.setVersion(2);
        active.setStatus("ACTIVE");
        UiConfigRelease pinned = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of()));

        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        when(context.releaseMapper().selectById("release-1"))
                .thenReturn(pinned);
        when(context.processImpactPort().analyzeFormImpact(
                "form-1")).thenReturn(new UiHotfixProcessImpact(
                        List.of(processTarget(
                                "release-1",
                                1)),
                        1,
                        1L,
                        0L,
                        "impact-force-2"));

        UiConfigPublishRequest request =
                new UiConfigPublishRequest();
        request.setReleaseMode(UiConfigReleaseService.HOTFIX);
        UiConfigPublishPreviewDTO preview =
                context.service().publishPreview(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        request);

        assertTrue(preview.isCanPublish());
        assertTrue(preview.getBlockers().isEmpty());
        assertTrue(preview.getTargets().get(0).isCompatible());
        assertEquals(
                "FULL_SNAPSHOT",
                preview.getTargets().get(0).getApplicationMode());
        assertTrue(preview.getTargets().get(0)
                .getReviewNotes().stream().anyMatch(note ->
                        note.contains("缺少稳定条目")
                                && note.contains("完整快照覆盖")));
    }

    /**
     * 测试列表首次发布前的差异计算：
     * 验证没有激活版本时，允许场景差异被报告而不会修改不可变空集合。
     */
    @Test
    void reportsInitialListDiffWhenNoActiveReleaseExists() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        EntityListConfigService listConfigService =
                mock(EntityListConfigService.class);
        EntityListConfigDTO list = new EntityListConfigDTO();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setEntityCode("demo_entity");
        list.setListKey("default");
        list.setListName("默认列表");
        list.setAllowedScenes(List.of("PAGE", "DIALOG"));
        list.setFields(List.of());
        when(listConfigService.findById("list-1")).thenReturn(list);
        when(releaseMapper.findActive(
                UiConfigReleaseService.LIST, "list-1")).thenReturn(null);

        UiConfigReleaseService service = new UiConfigReleaseService(
                releaseMapper,
                mock(UiConfigHotfixTargetMapper.class),
                mock(UiConfigReleaseAuditMapper.class),
                new UiConfigDataSourceReferenceValidator(
                        mock(UiDataSourceDefinitionMapper.class),
                        codec),
                new UiEventBindingSnapshotService(
                        mock(UiEventBindingMapper.class),
                        mock(UiDataSourceDefinitionMapper.class),
                        codec),
                new UiConfigSnapshotSupport(codec, objectMapper),
                mock(UiComponentTemplateMapper.class),
                mock(UiComponentTemplateVersionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionMapper.class),
                mock(EntityFormService.class),
                mock(EntityFormNodeService.class),
                mock(EntityFormConfigurationValidator.class),
                mock(UiExtensionDefinitionService.class),
                listConfigService,
                mock(EntityListConfigurationValidator.class),
                new UiConfigSemanticPatchService(codec),
                mock(UiHotfixProcessImpactPort.class),
                mock(UiConfigurationAccessService.class),
                mock(UiReleaseResolutionTokenService.class),
                mock(FormSubmissionTraceService.class),
                codec,
                objectMapper,
                mock(MigrationAssetHandler.class));
        attachViewCompositionService(service);

        UiConfigDiffDTO diff = service.diff(
                UiConfigReleaseService.LIST, "list-1");

        assertTrue(diff.isChanged());
        assertTrue(diff.getChangedItems().stream().anyMatch(item ->
                "allowedScenes".equals(item.getSection())
                        && "PAGE".equals(item.getId())
                        && "ADDED".equals(item.getChangeType())));
        assertTrue(diff.getChangedItems().stream().anyMatch(item ->
                "allowedScenes".equals(item.getSection())
                        && "DIALOG".equals(item.getId())
                        && "ADDED".equals(item.getChangeType())));
    }

    /**
     * 测试快照内容哈希与存储哈希不一致时拒绝激活：
     * 验证抛出 IllegalArgumentException 且消息包含"完整性校验失败"。
     */
    @Test
    void rejectsActivationWhenSnapshotHashDoesNotMatch() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(node("field", null, "FIELD"))));
        release.setContentHash("tampered-hash");
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("完整性校验失败"));
    }

    /**
     * 测试解析钉住的未激活表单发布并递归加载其节点：
     * 验证返回的表单与节点层级、pinned 标志符合预期。
     */
    @Test
    void resolvesPinnedInactiveFormReleaseWithRecursiveNodes() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-7",
                formSnapshot(List.of(node("section", null, "SECTION"))));
        release.setVersion(7);
        when(context.releaseMapper().selectById("release-7"))
                .thenReturn(release);

        ResolvedEntityFormRelease resolution =
                context.service().resolveRuntimeFormRelease(
                "form-1",
                "release-7",
                7);

        EntityForm form = resolution.form();
        assertEquals("form-1", form.getId());
        assertEquals(1, form.getNodes().size());
        assertEquals("section", form.getNodes().get(0).getId());
        assertTrue(resolution.pinned());
    }

    /**
     * 测试钉住发布的版本号与流程快照版本不一致时拒绝解析：
     * 验证抛出 IllegalArgumentException 且消息包含"版本号与流程快照不一致"。
     */
    @Test
    void rejectsPinnedReleaseVersionMismatch() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-7",
                formSnapshot(List.of()));
        release.setVersion(7);
        when(context.releaseMapper().selectById("release-7"))
                .thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().resolveRuntimeForm(
                        "form-1",
                        "release-7",
                        8));

        assertTrue(exception.getMessage().contains("版本号与流程快照不一致"));
    }

    @Test
    void unsignedRuntimeRequestCannotReadHistoricalFormRelease() {
        TestContext context = context();
        UiConfigRelease active = release(
                context.codec(),
                "release-active",
                formSnapshot(List.of()));
        active.setVersion(2);
        active.setStatus("ACTIVE");
        when(context.releaseMapper().findActive("FORM", "form-1"))
                .thenReturn(active);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().runtimeFormRelease(
                        "form-1",
                        "release-history",
                        1));

        assertEquals("FORM_RELEASE_CONFLICT", exception.getErrorCode());
    }

    @Test
    void signedRuntimeReleaseKeepsParentTokenExpiryForDerivedContext() {
        TestContext context = context();
        UiConfigRelease pinned = release(
                context.codec(),
                "release-3",
                formSnapshot(List.of()));
        pinned.setVersion(3);
        when(context.releaseMapper().selectById("release-3"))
                .thenReturn(pinned);
        long now = Instant.now().getEpochSecond();
        long sessionExpiresAt = now + 1_800L;
        UiRuntimeResolutionContext runtimeContext =
                UiRuntimeResolutionContext.standalone();
        when(context.resolutionTokenService().verify("session-bound-token"))
                .thenReturn(new UiReleaseResolutionTokenService.Claims(
                        runtimeContext.purpose(),
                        null,
                        null,
                        "form-1",
                        "release-3",
                        3,
                        0,
                        "user-1",
                        "session-1",
                        "view-release-1",
                        now,
                        sessionExpiresAt));
        when(context.resolutionTokenService().issue(
                runtimeContext,
                "form-1",
                "release-3",
                3,
                1,
                Instant.ofEpochSecond(sessionExpiresAt)))
                .thenReturn("derived-session-bound-token");

        Map<String, Object> result = context.service().runtimeFormRelease(
                "form-1",
                "release-3",
                3,
                "session-bound-token");

        assertEquals(
                "derived-session-bound-token",
                result.get("releaseResolutionToken"));
        verify(context.resolutionTokenService()).issue(
                eq(runtimeContext),
                eq("form-1"),
                eq("release-3"),
                eq(3),
                eq(1),
                eq(Instant.ofEpochSecond(sessionExpiresAt)));
    }

    @Test
    void activeTaskUsesEffectiveHotfixSnapshot() {
        TestContext context = context();
        UiConfigRelease pinned = release(
                context.codec(),
                "release-2",
                formSnapshot(List.of(labelNode("旧标题"))));
        pinned.setVersion(2);
        when(context.releaseMapper().selectById("release-2"))
                .thenReturn(pinned);
        UiConfigHotfixTarget target = target(
                context.codec(),
                "target-1",
                "hotfix-3",
                "release-2",
                2,
                formSnapshot(List.of(labelNode("修复标题"))));
        when(context.hotfixTargetMapper().findActiveTarget(
                "FORM",
                "form-1",
                "history-1"))
                .thenReturn(target);

        ResolvedEntityFormRelease resolved =
                context.service().resolveRuntimeFormRelease(
                        "form-1",
                        "release-2",
                        2,
                        new UiRuntimeResolutionContext(
                                UiRuntimePurpose.ACTIVE_TASK,
                                "history-1",
                                "task-1"));

        assertTrue(resolved.hotfixApplied());
        assertEquals("hotfix-3", resolved.effectiveReleaseId());
        assertEquals(
                "修复标题",
                context.codec().readObject(
                        resolved.form().getNodes().get(0)
                                .getPropsDocument(),
                                "测试节点属性").get("label"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishedHotfixImmediatelyFeedsNewInstanceAndActiveTaskRuntime() {
        TestContext context = context();
        EntityForm draft = form();
        when(context.formService().getById("form-1"))
                .thenReturn(draft);

        Map<String, Object> activeSnapshot =
                context.codec().readObject(
                        context.codec().write(
                                context.service().draftSnapshot(
                                        UiConfigReleaseService.FORM,
                                        "form-1"),
                                "测试表单热修复发布基线"),
                        "测试表单热修复发布基线");
        Map<String, Object> activeNode =
                (Map<String, Object>) ((List<?>) activeSnapshot.get(
                        "nodes")).get(0);
        Map<String, Object> activeProps =
                context.codec().readObject(
                        String.valueOf(activeNode.get(
                                "propsDocument")),
                        "测试表单热修复发布基线节点");
        activeProps.put("label", "发布前标题");
        activeNode.put(
                "propsDocument",
                context.codec().write(
                        activeProps,
                        "测试表单热修复发布基线节点"));

        UiConfigRelease active = release(
                context.codec(),
                "release-2",
                activeSnapshot);
        active.setVersion(2);
        active.setStatus("ACTIVE");
        when(context.releaseMapper().findActive(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(active);
        when(context.releaseMapper().findReleases(
                UiConfigReleaseService.FORM,
                "form-1")).thenReturn(List.of(active));
        when(context.releaseMapper().selectById("release-2"))
                .thenReturn(active);

        UiHotfixProcessTarget processTarget =
                new UiHotfixProcessTarget(
                        "history-1",
                        "process-1",
                        "expense-flow",
                        "费用审批",
                        2,
                        "deployment-2",
                        "release-2",
                        2,
                        List.of("Task_Approve"),
                        true,
                        1L,
                        3L);
        when(context.processImpactPort().analyzeFormImpact(
                "form-1")).thenReturn(new UiHotfixProcessImpact(
                        List.of(processTarget),
                        1,
                        1L,
                        3L,
                        "impact-1"));

        AtomicReference<UiConfigHotfixTarget> savedTarget =
                new AtomicReference<>();
        when(context.hotfixTargetMapper().findActiveTarget(
                UiConfigReleaseService.FORM,
                "form-1",
                "history-1"))
                .thenAnswer(ignored -> savedTarget.get());
        doAnswer(invocation -> {
            UiConfigRelease release =
                    invocation.getArgument(0);
            release.setId("hotfix-3");
            return 1;
        }).when(context.releaseMapper()).insert(
                any(UiConfigRelease.class));
        doAnswer(invocation -> {
            UiConfigHotfixTarget target =
                    invocation.getArgument(0);
            target.setId("target-1");
            savedTarget.set(target);
            return 1;
        }).when(context.hotfixTargetMapper()).insert(
                any(UiConfigHotfixTarget.class));

        UiConfigPublishRequest previewRequest =
                new UiConfigPublishRequest();
        previewRequest.setReleaseMode(
                UiConfigReleaseService.HOTFIX);
        UiConfigPublishPreviewDTO preview =
                context.service().publishPreview(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        previewRequest);
        UiConfigPublishRequest publishRequest =
                new UiConfigPublishRequest();
        publishRequest.setReleaseMode(
                UiConfigReleaseService.HOTFIX);
        publishRequest.setRolloutScope("ACTIVE_AND_FUTURE");
        publishRequest.setExpectedActiveReleaseId(
                preview.getActiveReleaseId());
        publishRequest.setExpectedDraftHash(
                preview.getDraftHash());
        publishRequest.setImpactToken(
                preview.getImpactToken());

        UiConfigRelease published =
                context.service().publish(
                        UiConfigReleaseService.FORM,
                        "form-1",
                        publishRequest);

        assertEquals("hotfix-3", published.getId());
        assertEquals(3, published.getVersion());
        assertEquals(
                UiConfigReleaseService.HOTFIX,
                published.getReleaseMode());
        assertEquals(
                "hotfix-3",
                savedTarget.get().getHotfixReleaseId());
        assertEquals(
                "release-2",
                savedTarget.get().getPinnedReleaseId());

        for (UiRuntimePurpose purpose : List.of(
                UiRuntimePurpose.NEW_INSTANCE,
                UiRuntimePurpose.ACTIVE_TASK)) {
            ResolvedEntityFormRelease resolved =
                    context.service().resolveRuntimeFormRelease(
                            "form-1",
                            "release-2",
                            2,
                            new UiRuntimeResolutionContext(
                                    purpose,
                                    "history-1",
                                    "Task_Approve"));
            assertTrue(resolved.hotfixApplied());
            assertEquals(
                    "hotfix-3",
                    resolved.effectiveReleaseId());
            assertEquals(
                    "名称",
                    context.codec().readObject(
                            resolved.form().getNodes().get(0)
                                    .getPropsDocument(),
                            "测试热修复运行时节点").get("label"));
        }
    }

    @Test
    void historicalPurposeAlwaysUsesOriginalPinnedSnapshot() {
        TestContext context = context();
        UiConfigRelease pinned = release(
                context.codec(),
                "release-2",
                formSnapshot(List.of(labelNode("历史标题"))));
        pinned.setVersion(2);
        when(context.releaseMapper().selectById("release-2"))
                .thenReturn(pinned);

        ResolvedEntityFormRelease resolved =
                context.service().resolveRuntimeFormRelease(
                        "form-1",
                        "release-2",
                        2,
                        new UiRuntimeResolutionContext(
                                UiRuntimePurpose.HISTORICAL,
                                "history-1",
                                "task-1"));

        assertFalse(resolved.hotfixApplied());
        assertEquals(
                "历史标题",
                context.codec().readObject(
                        resolved.form().getNodes().get(0)
                                .getPropsDocument(),
                        "测试节点属性").get("label"));
    }

    @Test
    void trustedEffectiveFormReleaseUsesExactTargetSnapshotAfterRollback() {
        TestContext context = context();
        UiConfigRelease pinned = release(
                context.codec(),
                "release-2",
                formSnapshot(List.of(labelNode("基础版本"))));
        pinned.setVersion(2);
        UiConfigRelease genericHotfix = release(
                context.codec(),
                "hotfix-3",
                formSnapshot(List.of(labelNode("热修复发布自身快照"))));
        genericHotfix.setVersion(3);
        genericHotfix.setReleaseMode(UiConfigReleaseService.HOTFIX);
        when(context.releaseMapper().selectById("release-2"))
                .thenReturn(pinned);
        when(context.releaseMapper().selectById("hotfix-3"))
                .thenReturn(genericHotfix);

        UiConfigHotfixTarget target = target(
                context.codec(),
                "target-1",
                "hotfix-3",
                "release-2",
                2,
                formSnapshot(List.of(labelNode("目标有效快照"))));
        // 终检时 target 可能刚被回滚，但同一次提交不能因此切换规则快照。
        target.setStatus("ROLLED_BACK");
        when(context.hotfixTargetMapper().selectById("target-1"))
                .thenReturn(target);

        ResolvedEntityFormRelease resolved = context.service()
                .resolveTrustedEffectiveFormRelease(
                        "form-1",
                        "release-2",
                        2,
                        "hotfix-3",
                        target.getEffectiveContentHash(),
                        "target-1");

        assertEquals("target-1", resolved.hotfixTargetId());
        assertEquals(
                target.getEffectiveContentHash(),
                resolved.effectiveContentHash());
        assertEquals(
                "目标有效快照",
                context.codec().readObject(
                        resolved.form().getNodes().get(0)
                                .getPropsDocument(),
                        "测试节点属性").get("label"));
        verify(context.hotfixTargetMapper(), never())
                .findActiveTarget(any(), any(), any());

        assertThrows(
                IllegalArgumentException.class,
                () -> context.service()
                        .resolveTrustedEffectiveFormRelease(
                                "form-1",
                                "release-2",
                                2,
                                "hotfix-3",
                                "wrong-hash",
                                "target-1"));
    }

    @Test
    void corruptHotfixTargetFailsClosedInsteadOfSilentlyUsingPinnedSnapshot() {
        TestContext context = context();
        UiConfigRelease pinned = release(
                context.codec(),
                "release-2",
                formSnapshot(List.of(labelNode("原始标题"))));
        pinned.setVersion(2);
        when(context.releaseMapper().selectById("release-2"))
                .thenReturn(pinned);
        UiConfigHotfixTarget target = target(
                context.codec(),
                "target-1",
                "hotfix-3",
                "release-2",
                2,
                formSnapshot(List.of(labelNode("修复标题"))));
        target.setEffectiveContentHash("tampered");
        when(context.hotfixTargetMapper().findActiveTarget(
                "FORM",
                "form-1",
                "history-1"))
                .thenReturn(target);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> context.service().resolveRuntimeFormRelease(
                        "form-1",
                        "release-2",
                        2,
                        new UiRuntimeResolutionContext(
                                UiRuntimePurpose.ACTIVE_TASK,
                                "history-1",
                                "task-1")));

        assertTrue(exception.getMessage().contains(
                "热修复运行时快照解析失败"));
    }

    @Test
    void mismatchedHotfixTargetFailsClosed() {
        TestContext context = context();
        UiConfigRelease pinned = release(
                context.codec(),
                "release-2",
                formSnapshot(List.of(labelNode("原始标题"))));
        pinned.setVersion(2);
        when(context.releaseMapper().selectById("release-2"))
                .thenReturn(pinned);
        UiConfigHotfixTarget target = target(
                context.codec(),
                "target-1",
                "hotfix-3",
                "release-other",
                8,
                formSnapshot(List.of(labelNode("修复标题"))));
        when(context.hotfixTargetMapper().findActiveTarget(
                "FORM",
                "form-1",
                "history-1"))
                .thenReturn(target);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> context.service().resolveRuntimeFormRelease(
                        "form-1",
                        "release-2",
                        2,
                        new UiRuntimeResolutionContext(
                                UiRuntimePurpose.NEW_INSTANCE,
                                "history-1",
                                "task-1")));

        assertTrue(exception.getMessage().contains(
                "热修复目标与流程钉定表单版本不一致"));
    }

    @Test
    void rejectsTargetlessHotfixRollbackWhenReleaseIsNoLongerActive() {
        TestContext context = context();
        UiConfigRelease hotfix = release(
                context.codec(),
                "hotfix-2",
                formSnapshot(List.of(labelNode("修复标题"))));
        hotfix.setReleaseMode("HOTFIX");
        hotfix.setBaseReleaseId("release-1");
        UiConfigRelease current = release(
                context.codec(),
                "release-3",
                formSnapshot(List.of(labelNode("后续标题"))));
        current.setStatus("ACTIVE");
        when(context.releaseMapper().selectById("hotfix-2"))
                .thenReturn(hotfix);
        when(context.hotfixTargetMapper().findByHotfixReleaseId(
                "hotfix-2")).thenReturn(List.of());
        when(context.releaseMapper().findActive(
                "FORM",
                "form-1")).thenReturn(current);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().rollbackHotfix(
                        "FORM",
                        "form-1",
                        "hotfix-2",
                        "测试越序回滚"));

        assertEquals(
                "HOTFIX_ROLLBACK_ORDER_CONFLICT",
                exception.getErrorCode());
    }

    /**
     * 测试激活时 TAB 节点不在 TAB_SET 内被拒绝：
     * 验证抛出 IllegalArgumentException 且消息包含"TAB 节点只能位于 TAB_SET"。
     */
    @Test
    void rejectsActivationWhenTabIsOutsideTabSet() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(node("tab", null, "TAB"))));
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("TAB 节点只能位于 TAB_SET"));
    }

    /**
     * 测试激活时节点嵌套层级超过 8 层被拒绝：
     * 验证抛出 IllegalArgumentException 且消息包含"不能超过 8 层"。
     */
    @Test
    void rejectsActivationWhenSnapshotExceedsEightLevels() {
        TestContext context = context();
        List<Map<String, Object>> nodes = new ArrayList<>();
        String parentId = null;
        for (int index = 1; index <= 9; index++) {
            String id = "section-" + index;
            nodes.add(node(id, parentId, "SECTION"));
            parentId = id;
        }
        UiConfigRelease release = release(
                context.codec(), "release-1", formSnapshot(nodes));
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("不能超过 8 层"));
    }

    /**
     * 测试激活时叶子节点包含子节点被拒绝：
     * 验证抛出 IllegalArgumentException 且消息包含"不能直接包含"。
     */
    @Test
    void rejectsActivationWhenLeafNodeContainsChild() {
        TestContext context = context();
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(
                        node("text", null, "TEXT"),
                        node("field", "text", "FIELD"))));
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("不能直接包含"));
    }

    /**
     * 测试激活时已发布表单存在循环引用被拒绝：
     * 验证抛出 IllegalArgumentException 且消息包含"存在循环"。
     */
    @Test
    void rejectsActivationWhenPublishedFormReferencesCycle() {
        TestContext context = context();
        UiConfigRelease target = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(referenceNode(
                        "reference-form-2", "form-2"))));
        UiConfigRelease formTwo = release(
                context.codec(),
                "release-2",
                formSnapshot(List.of(referenceNode(
                        "reference-form-1", "form-1"))));
        formTwo.setConfigId("form-2");
        when(context.releaseMapper().selectById("release-1")).thenReturn(target);
        when(context.releaseMapper().findActive("FORM", "form-2"))
                .thenReturn(formTwo);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("存在循环"));
    }

    /**
     * 测试激活时跨表单嵌套层级超过 8 层被拒绝：
     * 验证抛出 IllegalArgumentException 且消息包含"跨表单嵌套层级不能超过 8 层"。
     */
    @Test
    void rejectsActivationWhenPublishedFormReferencesExceedEightLevels() {
        TestContext context = context();
        UiConfigRelease target = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(referenceNode(
                        "reference-form-2", "form-2"))));
        when(context.releaseMapper().selectById("release-1")).thenReturn(target);
        for (int index = 2; index <= 8; index++) {
            UiConfigRelease referenced = release(
                    context.codec(),
                    "release-" + index,
                    formSnapshot(List.of(referenceNode(
                            "reference-form-" + (index + 1),
                            "form-" + (index + 1)))));
            referenced.setConfigId("form-" + index);
            when(context.releaseMapper().findActive(
                    "FORM", "form-" + index))
                    .thenReturn(referenced);
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("跨表单嵌套层级不能超过 8 层"));
    }

    /**
     * 测试发布时节点引用的组件模板不存在被拒绝：
     * 验证抛出 IllegalArgumentException 且消息包含"模板不存在或未启用"。
     */
    @Test
    void rejectsPublishWhenTemplateDoesNotExist() {
        TestContext context = context();
        EntityForm form = form();
        form.getNodes().get(0).setTemplateId("missing-template");
        form.getNodes().get(0).setTemplateVersion(1);
        when(context.formService().getById("form-1")).thenReturn(form);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().publish(
                        "FORM",
                        "form-1",
                        (String) null));

        assertTrue(exception.getMessage().contains("模板不存在或未启用"));
    }

    /**
     * 测试激活时模板类型与节点类型不兼容被拒绝：
     * 验证错误消息包含可在表单设计器中直接定位节点与模板的完整信息。
     */
    @Test
    void rejectsActivationWhenTemplateTypeIsIncompatible() {
        TestContext context = context();
        Map<String, Object> field = node("field", null, "FIELD");
        field.put("bindingRef", "customerName");
        field.put(
                "propsDocument",
                """
                {"fieldCode":"customerName","fieldName":"客户名称","label":"客户名称","fieldType":"STRING","componentType":"input"}
                """);
        field.put("templateId", "subform-template");
        field.put("templateVersion", 1);
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(field)));
        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("subform-template");
        template.setTemplateKey("CUSTOMER_SUB_FORM");
        template.setTemplateName("客户子表单模板");
        template.setTemplateType("SUB_FORM");
        template.setStatus("ACTIVE");
        template.setDeleted(0);
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);
        when(context.templateMapper().selectById("subform-template"))
                .thenReturn(template);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("表单节点“客户名称”"));
        assertTrue(exception.getMessage().contains("编码: customerName"));
        assertTrue(exception.getMessage().contains("节点类型: FIELD"));
        assertTrue(exception.getMessage().contains("节点ID: field"));
        assertTrue(exception.getMessage().contains("组件模板“客户子表单模板”"));
        assertTrue(exception.getMessage().contains("模板类型: SUB_FORM"));
        assertTrue(exception.getMessage().contains("“锁定模板”"));
    }

    /**
     * 测试激活时节点锁定的模板版本不存在被拒绝：
     * 验证抛出 IllegalArgumentException 且消息包含"模板版本不存在"。
     */
    @Test
    void rejectsActivationWhenLockedTemplateVersionDoesNotExist() {
        TestContext context = context();
        Map<String, Object> section = node("section", null, "SECTION");
        section.put("templateId", "section-template");
        section.put("templateVersion", 3);
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(section)));
        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("section-template");
        template.setTemplateType("FORM_SECTION");
        template.setStatus("ACTIVE");
        template.setDeleted(0);
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);
        when(context.templateMapper().selectById("section-template"))
                .thenReturn(template);
        when(context.templateVersionMapper().selectOne(any())).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("模板版本不存在"));
    }

    @Test
    void rejectsActivationWhenLegacySubListSnapshotHasNoPinnedRelease() {
        TestContext context = context();
        Map<String, Object> fieldNode =
                node("embedded-list", null, "FIELD");
        fieldNode.put(
                "propsDocument",
                context.codec().write(
                        Map.of(
                                "fieldCode", "embeddedList",
                                "fieldName", "子列表",
                                "fieldType", "SUB_LIST",
                                "componentType", "sub_list",
                                "componentProps", Map.of(
                                        "subListConfig", Map.of(
                                                "targetEntityId", "target-1",
                                                "targetEntityCode", "target_entity",
                                                "listKey", "default"))),
                        "测试子列表节点属性"));
        UiConfigRelease release = release(
                context.codec(),
                "release-sub-list",
                formSnapshot(List.of(fieldNode)));
        EntityDefinition target = new EntityDefinition();
        target.setId("target-1");
        target.setEntityCode("target_entity");
        EntityListConfig draftList = new EntityListConfig();
        draftList.setEntityId("target-1");
        draftList.setEntityCode("target_entity");
        draftList.setListKey("default");
        draftList.setPublishedVersion(0);
        when(context.releaseMapper().selectById("release-sub-list"))
                .thenReturn(release);
        when(context.entityDefinitionMapper().selectById("target-1"))
                .thenReturn(target);
        when(context.listConfigMapper().findByEntityIdAndListKey(
                "target-1",
                "default"))
                .thenReturn(draftList);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate(
                        "FORM",
                        "form-1",
                        "release-sub-list"));

        assertTrue(exception.getMessage().contains("缺少固定列表版本"));
    }

    @Test
    void rejectsActivationWhenSubListDoesNotAllowEmbeddedScene() {
        TestContext context = context();
        Map<String, Object> fieldNode =
                node("embedded-list-scene", null, "FIELD");
        fieldNode.put(
                "propsDocument",
                context.codec().write(
                        Map.of(
                                "fieldCode", "embeddedList",
                                "fieldName", "子列表",
                                "fieldType", "SUB_LIST",
                                "componentType", "sub_list",
                                "componentProps", Map.of(
                                        "subListConfig", Map.of(
                                                "targetEntityId", "target-1",
                                                "targetEntityCode", "target_entity",
                                                "listKey", "default",
                                                "listId", "target-list-1",
                                                "listReleaseId", "target-list-release-1",
                                                "listReleaseVersion", 1))),
                        "测试子列表节点属性"));
        UiConfigRelease formRelease = release(
                context.codec(),
                "release-sub-list-scene",
                formSnapshot(List.of(fieldNode)));
        EntityDefinition target = new EntityDefinition();
        target.setId("target-1");
        target.setEntityCode("target_entity");
        EntityListConfig targetList = new EntityListConfig();
        targetList.setId("target-list-1");
        targetList.setEntityId("target-1");
        targetList.setEntityCode("target_entity");
        targetList.setListKey("default");
        targetList.setPublishedVersion(1);
        targetList.setActiveReleaseId("target-list-release-1");
        UiConfigRelease listRelease = release(
                context.codec(),
                "target-list-release-1",
                Map.of(
                        "list",
                        Map.of(
                                "id", "target-list-1",
                                "entityId", "target-1",
                                "entityCode", "target_entity",
                                "listKey", "default",
                                "allowedScenes", List.of("PAGE"))));
        listRelease.setConfigType("LIST");
        listRelease.setConfigId("target-list-1");
        when(context.releaseMapper().selectById(
                "release-sub-list-scene"))
                .thenReturn(formRelease);
        when(context.releaseMapper().selectById(
                "target-list-release-1"))
                .thenReturn(listRelease);
        when(context.entityDefinitionMapper().selectById("target-1"))
                .thenReturn(target);
        when(context.listConfigMapper().findByEntityIdAndListKey(
                "target-1",
                "default"))
                .thenReturn(targetList);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate(
                        "FORM",
                        "form-1",
                        "release-sub-list-scene"));

        assertTrue(exception.getMessage().contains("EMBEDDED"));
    }

    @Test
    void activatesFormWithHistoricalPinnedSubListRelease() {
        TestContext context = context();
        Map<String, Object> fieldNode =
                node("embedded-list-history", null, "FIELD");
        fieldNode.put(
                "propsDocument",
                context.codec().write(
                        Map.of(
                                "fieldCode", "embeddedList",
                                "fieldName", "子列表",
                                "fieldType", "SUB_LIST",
                                "componentType", "sub_list",
                                "componentProps", Map.of(
                                        "subListConfig", Map.of(
                                                "targetEntityId", "target-1",
                                                "targetEntityCode", "target_entity",
                                                "listKey", "default",
                                                "listId", "target-list-1",
                                                "listReleaseId", "target-list-release-1",
                                                "listReleaseVersion", 1))),
                        "测试历史子列表节点属性"));
        UiConfigRelease formRelease = release(
                context.codec(),
                "release-sub-list-history",
                formSnapshot(List.of(fieldNode)));
        EntityDefinition target = new EntityDefinition();
        target.setId("target-1");
        target.setEntityCode("target_entity");
        EntityListConfig targetList = new EntityListConfig();
        targetList.setId("target-list-1");
        targetList.setEntityId("target-1");
        targetList.setEntityCode("target_entity");
        targetList.setListKey("default");
        targetList.setPublishedVersion(2);
        targetList.setActiveReleaseId("target-list-release-2");
        UiConfigRelease historicalListRelease = release(
                context.codec(),
                "target-list-release-1",
                Map.of(
                        "list",
                        Map.of(
                                "id", "target-list-1",
                                "entityId", "target-1",
                                "entityCode", "target_entity",
                                "listKey", "default",
                                "allowedScenes", List.of("EMBEDDED"))));
        historicalListRelease.setConfigType("LIST");
        historicalListRelease.setConfigId("target-list-1");
        when(context.releaseMapper().selectById(
                "release-sub-list-history"))
                .thenReturn(formRelease);
        when(context.releaseMapper().selectById(
                "target-list-release-1"))
                .thenReturn(historicalListRelease);
        when(context.releaseMapper().update(any(), any()))
                .thenReturn(1);
        when(context.entityDefinitionMapper().selectById("target-1"))
                .thenReturn(target);
        when(context.listConfigMapper().findByEntityIdAndListKey(
                "target-1",
                "default"))
                .thenReturn(targetList);

        UiConfigRelease activated = context.service().activate(
                "FORM",
                "form-1",
                "release-sub-list-history");

        assertEquals("release-sub-list-history", activated.getId());
        assertEquals("ACTIVE", activated.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void formDraftSnapshotContainsFormScopedEntitySelectionBinding() {
        TestContext context = context();
        when(context.formService().getById("form-1"))
                .thenReturn(form());
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-1");
        binding.setOwnerType("FORM");
        binding.setOwnerId("form-1");
        binding.setTargetType("FIELD");
        binding.setTargetKey("name");
        binding.setEventCode("ENTITY_SELECTED");
        binding.setInheritanceMode("INHERIT");
        binding.setStepsDocument(context.codec().write(
                List.of(Map.of(
                        "stepCode",
                        "ENTITY_SELECTION_FILL",
                        "strategy",
                        "AFTER",
                        "outputMapping",
                        List.of(Map.of(
                                "sourcePath",
                                "selection.data.phone",
                                "targetPath",
                                "form.phone")))),
                "测试选择后回填步骤"));
        binding.setRevision(3);
        when(context.eventBindingMapper().findForSnapshot(
                "FORM",
                "form-1",
                "entity-1"))
                .thenReturn(List.of(binding));

        Map<String, Object> snapshot =
                context.service().draftSnapshot(
                        UiConfigReleaseService.FORM,
                        "form-1");
        Map<String, Object> saved =
                (Map<String, Object>) ((List<?>) snapshot.get(
                        "eventBindings")).get(0);

        assertEquals("FORM", saved.get("ownerType"));
        assertEquals("form-1", saved.get("ownerId"));
        assertEquals("ENTITY_SELECTED", saved.get("eventCode"));
        assertEquals(
                "ENTITY_SELECTION_FILL",
                ((Map<String, Object>) ((List<?>) saved.get(
                        "steps")).get(0)).get("stepCode"));
    }

    @Test
    void eventSnapshotTracksHotfixAndRollbackForReusedProcessForm() {
        TestContext context = context();
        Map<String, Object> pinnedSnapshot =
                formSnapshot(List.of(labelNode("原始标题")));
        pinnedSnapshot.put(
                "eventBindings",
                List.of(eventBindingSnapshot("form.originalPhone")));
        UiConfigRelease pinned = release(
                context.codec(),
                "release-2",
                pinnedSnapshot);
        pinned.setVersion(2);
        when(context.releaseMapper().selectById("release-2"))
                .thenReturn(pinned);

        Map<String, Object> effectiveSnapshot =
                formSnapshot(List.of(labelNode("热修复标题")));
        effectiveSnapshot.put(
                "eventBindings",
                List.of(eventBindingSnapshot("form.hotfixPhone")));
        UiConfigHotfixTarget target = target(
                context.codec(),
                "target-1",
                "hotfix-3",
                "release-2",
                2,
                effectiveSnapshot);
        when(context.hotfixTargetMapper().selectById("target-1"))
                .thenReturn(target);
        when(context.hotfixTargetMapper().findActiveTarget(
                "FORM",
                "form-1",
                "history-1"))
                .thenReturn(target, target, null);
        when(context.resolutionTokenService().verify("task-a-token"))
                .thenReturn(tokenClaims("Task_A"));
        when(context.resolutionTokenService().verify("task-b-token"))
                .thenReturn(tokenClaims("Task_B"));
        when(context.resolutionTokenService().verify("rolled-back-token"))
                .thenReturn(tokenClaims("Task_A"));

        UiConfigReleaseService.ResolvedUiEventSnapshot taskA =
                context.service().resolveRuntimeEventSnapshot(
                        "form-1",
                        "release-2",
                        2,
                        "task-a-token");
        UiConfigReleaseService.ResolvedUiEventSnapshot taskB =
                context.service().resolveRuntimeEventSnapshot(
                        "form-1",
                        "release-2",
                        2,
                        "task-b-token");
        UiConfigReleaseService.ResolvedUiEventSnapshot rolledBack =
                context.service().resolveRuntimeEventSnapshot(
                        "form-1",
                        "release-2",
                        2,
                        "rolled-back-token");

        assertTrue(taskA.hotfixApplied());
        assertTrue(taskB.hotfixApplied());
        assertEquals(
                "form.hotfixPhone",
                firstSelectionTarget(taskA.snapshot()));
        assertEquals(
                firstSelectionTarget(taskA.snapshot()),
                firstSelectionTarget(taskB.snapshot()));
        assertFalse(rolledBack.hotfixApplied());
        assertEquals(
                "form.originalPhone",
                firstSelectionTarget(rolledBack.snapshot()));
    }

    @Test
    void standaloneEventRejectsClientSelectedHistoricalRelease() {
        TestContext context = context();
        UiConfigRelease active = release(
                context.codec(),
                "release-active",
                formSnapshot(List.of(labelNode("当前标题"))));
        active.setStatus("ACTIVE");
        when(context.releaseMapper().findActive(
                "FORM",
                "form-1"))
                .thenReturn(active);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service()
                        .resolveRuntimeEventSnapshot(
                                "form-1",
                                "release-history",
                                1,
                                null));

        assertEquals(
                "UI_EVENT_RELEASE_CONFLICT",
                exception.getErrorCode());
    }

    @Test
    void projectsUiReleaseAuditWithNativeSourcePointer() {
        TestContext context = context();
        SystemAuditPort auditPort = mock(SystemAuditPort.class);
        ReflectionTestUtils.setField(
                context.service(), "auditPort", auditPort);

        UiConfigReleaseAudit nativeAudit = new UiConfigReleaseAudit();
        nativeAudit.setId("ui-audit-1");
        nativeAudit.setConfigType("FORM");
        nativeAudit.setConfigId("form-1");
        nativeAudit.setReleaseId("release-7");
        nativeAudit.setOperation("PUBLISH_STANDARD");
        nativeAudit.setRiskLevel("REVIEW");
        nativeAudit.setActorId("user-1");
        nativeAudit.setActorName("测试用户");
        nativeAudit.setTraceId("native-trace");

        try (OperationContextHolder.Scope ignored =
                     OperationContextHolder.open(
                             OperationContext.root(
                                     "operation-1", "http-trace-1"))) {
            ReflectionTestUtils.invokeMethod(
                    context.service(),
                    "recordUnifiedReleaseAudit",
                    nativeAudit);
        }

        ArgumentCaptor<SystemAuditEvent> captor =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(captor.capture());
        SystemAuditEvent event = captor.getValue();
        assertEquals("operation-1", event.operationId());
        assertEquals("http-trace-1", event.traceId());
        assertEquals("UI_CONFIG_RELEASE", event.targetType());
        assertEquals("release-7", event.targetId());
        assertEquals("UI_CONFIG_RELEASE_AUDIT",
                event.sourcePointer().sourceType());
        assertEquals("ui-audit-1",
                event.sourcePointer().sourceId());
        assertEquals("ui-audit-1",
                event.sourcePointer().sourceEventId());
        assertEquals("HIGH", event.riskLevel().name());
        assertNull(event.beforeData());
        assertNull(event.afterData());
    }

    private UiHotfixProcessTarget processTarget(
            String pinnedReleaseId,
            Integer pinnedReleaseVersion) {
        return new UiHotfixProcessTarget(
                "history-1",
                "process-1",
                "expense-flow",
                "费用审批",
                1,
                "deployment-1",
                pinnedReleaseId,
                pinnedReleaseVersion,
                List.of("Task_Approve"),
                true,
                1L,
                0L);
    }

    private UiConfigPublishRequest hotfixPublishRequest(
            UiConfigPublishPreviewDTO preview) {
        UiConfigPublishRequest request =
                new UiConfigPublishRequest();
        request.setReleaseMode(UiConfigReleaseService.HOTFIX);
        request.setRolloutScope(
                UiConfigReleaseService.ACTIVE_AND_FUTURE);
        request.setExpectedActiveReleaseId(
                preview.getActiveReleaseId());
        request.setExpectedDraftHash(preview.getDraftHash());
        request.setImpactToken(preview.getImpactToken());
        return request;
    }

    /** 构造测试上下文，装配被测服务与各 Mock 依赖 */
    private TestContext context() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        UiConfigHotfixTargetMapper hotfixTargetMapper =
                mock(UiConfigHotfixTargetMapper.class);
        UiComponentTemplateMapper templateMapper =
                mock(UiComponentTemplateMapper.class);
        UiComponentTemplateVersionMapper templateVersionMapper =
                mock(UiComponentTemplateVersionMapper.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityListConfigService listConfigService =
                mock(EntityListConfigService.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityListConfigMapper listConfigMapper =
                mock(EntityListConfigMapper.class);
        EntityDefinitionMapper entityDefinitionMapper =
                mock(EntityDefinitionMapper.class);
        UiHotfixProcessImpactPort processImpactPort =
                mock(UiHotfixProcessImpactPort.class);
        UiEventBindingMapper eventBindingMapper =
                mock(UiEventBindingMapper.class);
        UiDataSourceDefinitionMapper dataSourceDefinitionMapper =
                mock(UiDataSourceDefinitionMapper.class);
        UiReleaseResolutionTokenService resolutionTokenService =
                mock(UiReleaseResolutionTokenService.class);
        when(formMapper.selectByIdForUpdate("form-1"))
                .thenReturn(form());
        when(formMapper.update(any(), any())).thenReturn(1);
        when(listConfigMapper.update(any(), any())).thenReturn(1);
        UiConfigReleaseService service = new UiConfigReleaseService(
                releaseMapper,
                hotfixTargetMapper,
                mock(UiConfigReleaseAuditMapper.class),
                new UiConfigDataSourceReferenceValidator(
                        dataSourceDefinitionMapper,
                        codec),
                new UiEventBindingSnapshotService(
                        eventBindingMapper,
                        dataSourceDefinitionMapper,
                        codec),
                new UiConfigSnapshotSupport(codec, objectMapper),
                templateMapper,
                templateVersionMapper,
                formMapper,
                listConfigMapper,
                entityDefinitionMapper,
                formService,
                mock(EntityFormNodeService.class),
                mock(EntityFormConfigurationValidator.class),
                mock(UiExtensionDefinitionService.class),
                listConfigService,
                mock(EntityListConfigurationValidator.class),
                new UiConfigSemanticPatchService(codec),
                processImpactPort,
                mock(UiConfigurationAccessService.class),
                resolutionTokenService,
                mock(FormSubmissionTraceService.class),
                codec,
                objectMapper,
                mock(MigrationAssetHandler.class));
        UiViewCompositionService viewCompositionService =
                attachViewCompositionService(service);
        UiHotfixGovernanceService governanceService =
                mock(UiHotfixGovernanceService.class);
        when(governanceService.beginDirectPublish(any(), any()))
                .thenReturn("hotfix-request-1");
        ReflectionTestUtils.setField(
                service,
                "hotfixGovernanceService",
                governanceService);
        ReflectionTestUtils.setField(
                service,
                "listActionConfigService",
                new EntityListActionConfigService(
                        objectMapper,
                        entityDefinitionMapper,
                        listConfigMapper,
                        mock(EntityListRelationalConfigService.class),
                        List.of(),
                        List.of()));
        return new TestContext(
                service,
                releaseMapper,
                hotfixTargetMapper,
                templateMapper,
                templateVersionMapper,
                formService,
                listConfigService,
                formMapper,
                listConfigMapper,
                entityDefinitionMapper,
                processImpactPort,
                eventBindingMapper,
                dataSourceDefinitionMapper,
                resolutionTokenService,
                viewCompositionService,
                codec);
    }

    /**
     * 显式装配发布生命周期所需的关联内容服务。测试默认没有关联内容，因此
     * snapshot 返回空数组；依赖重钉定保持输入，避免用缺失依赖掩盖生产故障。
     */
    private UiViewCompositionService attachViewCompositionService(
            UiConfigReleaseService service) {
        UiViewCompositionService viewCompositionService =
                mock(UiViewCompositionService.class);
        when(viewCompositionService.snapshot(any(), any()))
                .thenReturn(List.of());
        when(viewCompositionService
                .normalizeSnapshotForCurrentDependencies(any()))
                .thenAnswer(invocation -> {
                    List<Map<String, Object>> items =
                            invocation.getArgument(0);
                    return items == null ? List.of() : List.copyOf(items);
                });
        ReflectionTestUtils.setField(
                service,
                "viewCompositionService",
                viewCompositionService);
        return viewCompositionService;
    }

    private Map<String, Object> eventBindingSnapshot(
            String targetPath) {
        return Map.of(
                "id", "binding-1",
                "ownerType", "FORM",
                "ownerId", "form-1",
                "targetType", "FIELD",
                "targetKey", "customerId",
                "eventCode", "ENTITY_SELECTED",
                "inheritanceMode", "INHERIT",
                "steps", List.of(Map.of(
                        "stepCode",
                        "ENTITY_SELECTION_FILL",
                        "strategy",
                        "AFTER",
                        "outputMapping",
                        List.of(Map.of(
                                "sourcePath",
                                "selection.data.phone",
                                "targetPath",
                                targetPath)))));
    }

    @SuppressWarnings("unchecked")
    private String firstSelectionTarget(
            Map<String, Object> snapshot) {
        Map<String, Object> binding =
                (Map<String, Object>) ((List<?>) snapshot.get(
                        "eventBindings")).get(0);
        Map<String, Object> step =
                (Map<String, Object>) ((List<?>) binding.get(
                        "steps")).get(0);
        Map<String, Object> mapping =
                (Map<String, Object>) ((List<?>) step.get(
                        "outputMapping")).get(0);
        return String.valueOf(mapping.get("targetPath"));
    }

    private UiReleaseResolutionTokenService.Claims tokenClaims(
            String nodeId) {
        return new UiReleaseResolutionTokenService.Claims(
                UiRuntimePurpose.ACTIVE_TASK,
                "history-1",
                nodeId,
                "form-1",
                "release-2",
                2,
                0,
                "user-1",
                null,
                null,
                1L,
                Long.MAX_VALUE);
    }

    /** 构造一个带完整性哈希的发布记录用于激活/解析测试 */
    private UiConfigRelease release(
            JsonDocumentCodec codec,
            String releaseId,
            Map<String, Object> snapshot) {
        String document = codec.canonicalize(
                codec.write(snapshot, "测试发布快照"), "测试发布快照");
        UiConfigRelease release = new UiConfigRelease();
        release.setId(releaseId);
        release.setConfigType("FORM");
        release.setConfigId("form-1");
        release.setVersion(1);
        release.setStatus("INACTIVE");
        release.setSnapshotDocument(document);
        release.setContentHash(sha256(document));
        return release;
    }

    private UiConfigRelease configRelease(
            JsonDocumentCodec codec,
            String releaseId,
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        String document = codec.canonicalize(
                codec.write(snapshot, "测试发布快照"),
                "测试发布快照");
        UiConfigRelease release = new UiConfigRelease();
        release.setId(releaseId);
        release.setConfigType(configType);
        release.setConfigId(configId);
        release.setVersion(3);
        release.setStatus("ACTIVE");
        release.setReleaseMode(UiConfigReleaseService.STANDARD);
        release.setSnapshotDocument(document);
        release.setContentHash(sha256(document));
        return release;
    }

    private String snapshotHash(
            JsonDocumentCodec codec,
            Map<String, Object> snapshot) {
        return sha256(codec.canonicalize(
                codec.write(snapshot, "测试草稿快照"),
                "测试草稿快照"));
    }

    private UiConfigDraftDiscardRequest discardRequest(
            int revision,
            String draftHash,
            String activeReleaseId) {
        UiConfigDraftDiscardRequest request =
                new UiConfigDraftDiscardRequest();
        request.setExpectedRevision(revision);
        request.setExpectedDraftHash(draftHash);
        request.setExpectedActiveReleaseId(activeReleaseId);
        request.setReason("测试撤销未发布修改");
        return request;
    }

    private UiEventBinding inheritedBinding(String stepsDocument) {
        UiEventBinding binding = new UiEventBinding();
        binding.setId("entity-binding-1");
        binding.setOwnerType("ENTITY");
        binding.setOwnerId("entity-1");
        binding.setTargetType("OWNER");
        binding.setTargetKey("");
        binding.setEventCode("FORM_OPEN");
        binding.setInheritanceMode("INHERIT");
        binding.setStepsDocument(stepsDocument);
        binding.setRevision(1);
        binding.setEnabled(true);
        binding.setDeleted(0);
        return binding;
    }

    private UiConfigHotfixTarget target(
            JsonDocumentCodec codec,
            String targetId,
            String hotfixReleaseId,
            String pinnedReleaseId,
            Integer pinnedReleaseVersion,
            Map<String, Object> snapshot) {
        String document = codec.canonicalize(
                codec.write(snapshot, "测试热修复快照"),
                "测试热修复快照");
        UiConfigHotfixTarget target =
                new UiConfigHotfixTarget();
        target.setId(targetId);
        target.setHotfixReleaseId(hotfixReleaseId);
        target.setConfigType(UiConfigReleaseService.FORM);
        target.setConfigId("form-1");
        target.setPinnedReleaseId(pinnedReleaseId);
        target.setPinnedReleaseVersion(pinnedReleaseVersion);
        target.setEffectiveSnapshotDocument(document);
        target.setEffectiveContentHash(sha256(document));
        target.setStatus("ACTIVE");
        return target;
    }

    /** 构造一个包含表单与指定节点列表的发布快照 Map */
    private Map<String, Object> formSnapshot(List<Map<String, Object>> nodes) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("id", "form-1");
        form.put("entityId", "entity-1");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("configType", "FORM");
        snapshot.put("form", form);
        snapshot.put("nodes", nodes);
        snapshot.put("legacyFields", List.of());
        return snapshot;
    }

    private EntityListConfigDTO listConfig(int defaultVisibleCount) {
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("defaultVisibleCount", defaultVisibleCount);
        Map<String, Object> viewConfig = new LinkedHashMap<>();
        viewConfig.put("search", search);
        EntityListConfigDTO list = new EntityListConfigDTO();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setEntityCode("demo_entity");
        list.setListKey("default");
        list.setListName("默认列表");
        list.setViewConfig(viewConfig);
        list.setFields(List.of());
        list.setToolbarConfig(List.of(listButton(
                "toolbar-create",
                "create",
                "新增数据",
                "entity:demo_entity:create",
                1,
                1_000_000L)));
        list.setRowActionConfig(List.of(listButton(
                "row-view",
                "view",
                "查看",
                "entity:demo_entity:view",
                1,
                1_000_000L)));
        return list;
    }

    private Map<String, Object> listButton(
            String id,
            String key,
            String label,
            String permission,
            int sort,
            long orderKey) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("id", id);
        button.put("key", key);
        button.put("type", "built-in");
        button.put("label", label);
        button.put("buttonType", "primary");
        button.put("link", false);
        button.put("perm", permission);
        button.put("sort", sort);
        button.put("enabled", true);
        button.put("orderKey", orderKey);
        return button;
    }

    /** 构造一个表单节点 Map，含 id、parentId、nodeType 等基础字段 */
    private Map<String, Object> node(
            String id,
            String parentId,
            String nodeType) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("formId", "form-1");
        node.put("parentId", parentId);
        node.put("nodeKey", id.replace('-', '_'));
        node.put("nodeType", nodeType);
        node.put("bindingType", "NONE");
        node.put("orderKey", 1_000_000);
        return node;
    }

    private Map<String, Object> labelNode(String label) {
        Map<String, Object> node =
                node("field", null, "FIELD");
        node.put(
                "propsDocument",
                "{\"label\":\"" + label + "\"}");
        return node;
    }

    /** 构造一个引用已发布表单的 SUB_FORM 节点 Map */
    private Map<String, Object> referenceNode(
            String id,
            String publishedFormId) {
        Map<String, Object> node = node(id, null, "SUB_FORM");
        node.put(
                "propsDocument",
                "{\"publishedFormId\":\"" + publishedFormId + "\"}");
        return node;
    }

    /** 计算字符串的 SHA-256 十六进制哈希，用于模拟发布快照的完整性哈希 */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 测试上下文记录，聚合被测服务与核心 Mock 依赖以便在各用例复用 */
    private record TestContext(
            UiConfigReleaseService service,
            UiConfigReleaseMapper releaseMapper,
            UiConfigHotfixTargetMapper hotfixTargetMapper,
            UiComponentTemplateMapper templateMapper,
            UiComponentTemplateVersionMapper templateVersionMapper,
            EntityFormService formService,
            EntityListConfigService listConfigService,
            EntityFormMapper formMapper,
            EntityListConfigMapper listConfigMapper,
            EntityDefinitionMapper entityDefinitionMapper,
            UiHotfixProcessImpactPort processImpactPort,
            UiEventBindingMapper eventBindingMapper,
            UiDataSourceDefinitionMapper dataSourceDefinitionMapper,
            UiReleaseResolutionTokenService resolutionTokenService,
            UiViewCompositionService viewCompositionService,
            JsonDocumentCodec codec) {
    }

    /** 构造包含字段与节点的标准测试表单 fixture */
    private EntityForm form() {
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setFormName("测试表单");
        form.setFormKey("test_form");
        form.setLayoutType("grid");
        form.setRevision(9);
        form.setActiveReleaseId("release-1");
        form.setDataSourceBindingsDocument(
                "{\"FORM_INIT\":{\"serviceId\":\"source-init\","
                        + "\"operationCode\":\"initializeForm\"}}");

        EntityFormField field = new EntityFormField();
        field.setId("node-1");
        field.setFormId("form-1");
        field.setFieldId("field-1");
        field.setFieldCode("name");
        field.setFieldName("实体字段名称");
        field.setFieldLabel("名称");
        field.setFieldType("STRING");
        field.setComponentType("input");
        field.setComponentProps("{}");
        field.setGridSpan(24);
        field.setSortOrder(0);
        field.setUpdateTime(LocalDateTime.now());
        form.setFields(List.of(field));

        EntityFormNode node = new EntityFormNode();
        node.setId("node-1");
        node.setFormId("form-1");
        node.setNodeKey("name");
        node.setNodeType("FIELD");
        node.setBindingType("ENTITY_FIELD");
        node.setBindingRef("name");
        node.setPropsDocument(
                "{\"fieldId\":\"field-1\",\"fieldCode\":\"name\","
                        + "\"label\":\"名称\",\"componentType\":\"input\","
                        + "\"gridSpan\":24}");
        node.setOrderKey(1_000_000L);
        node.setRevision(7);
        node.setUpdatedAt(LocalDateTime.now());
        form.setNodes(List.of(node));
        return form;
    }
}
