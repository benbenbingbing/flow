package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.response.UiDataSourceReferenceDTO;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiDataSourceReferenceServiceTest {

    private UiDataSourceDefinitionMapper dataSourceMapper;
    private UiEventBindingMapper bindingMapper;
    private EntityDefinitionMapper entityMapper;
    private EntityFormMapper formMapper;
    private EntityListConfigMapper listMapper;
    private UiConfigReleaseMapper releaseMapper;
    private UiConfigReleaseService releaseService;
    private UiConfigurationAccessService accessService;
    private EntityDefinitionAccessPolicy entityAccessPolicy;
    private JsonDocumentCodec codec;
    private UiDataSourceReferenceService service;

    @BeforeEach
    void setUp() {
        dataSourceMapper = mock(UiDataSourceDefinitionMapper.class);
        bindingMapper = mock(UiEventBindingMapper.class);
        entityMapper = mock(EntityDefinitionMapper.class);
        formMapper = mock(EntityFormMapper.class);
        listMapper = mock(EntityListConfigMapper.class);
        releaseMapper = mock(UiConfigReleaseMapper.class);
        releaseService = mock(UiConfigReleaseService.class);
        accessService = mock(UiConfigurationAccessService.class);
        entityAccessPolicy = mock(EntityDefinitionAccessPolicy.class);
        ObjectMapper objectMapper = new ObjectMapper();
        codec = new JsonDocumentCodec(objectMapper);
        service = new UiDataSourceReferenceService(
                dataSourceMapper,
                bindingMapper,
                entityMapper,
                formMapper,
                listMapper,
                releaseMapper,
                releaseService,
                accessService,
                entityAccessPolicy,
                codec,
                new UiConfigSnapshotSupport(codec, objectMapper));
    }

    @Test
    void returnsPublishedFormReferenceAndEffectiveChain() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding binding = binding(
                "binding-form", "FORM", "form-1", "FORM_OPEN",
                step("step-load", "load", "加载详情"));
        EntityDefinition entity = entity();
        EntityForm form = form("form-1", "release-1");
        UiConfigRelease release = release(
                "release-1", "FORM", "form-1", 3);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(binding));
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(entityMapper.selectById("entity-1")).thenReturn(entity);
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(binding))));

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertEquals("binding-form:step-load", reference.getReferenceId());
        assertEquals("验收查询", reference.getServiceName());
        assertEquals("加载", reference.getOperationName());
        assertEquals("PUBLISHED_MATCH", reference.getLifecycleStatus());
        assertEquals("ACTIVE", reference.getEffectiveStatus());
        assertTrue(reference.isPublished());
        assertEquals(Boolean.TRUE, reference.getEffective());
        assertEquals(3, reference.getActiveReleaseVersion());
        assertEquals(List.of("默认表单 (FORM)"),
                reference.getEffectiveContexts());
        assertEquals(1, reference.getContexts().get(0)
                .getEffectiveChain().size());
        assertEquals("binding-form", reference.getContexts().get(0)
                .getEffectiveChain().get(0).getBindingId());
    }

    @Test
    void reportsEntityDefaultAsShadowedByFormReplace() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding entityBinding = binding(
                "binding-entity", "ENTITY", "entity-1", "FORM_OPEN",
                step("step-default", "load", "默认加载"));
        UiEventBinding formReplace = binding(
                "binding-local", "FORM", "form-1", "FORM_OPEN",
                Map.of(
                        "stepCode", "step-local",
                        "name", "本地加载",
                        "strategy", "BEFORE",
                        "serviceId", "source-local",
                        "operationCode", "loadLocal",
                        "order", 10,
                        "failurePolicy", "STOP"));
        formReplace.setInheritanceMode("REPLACE");
        EntityDefinition entity = entity();
        EntityForm form = form("form-1", "release-1");
        UiConfigRelease release = release(
                "release-1", "FORM", "form-1", 4);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString()))
                .thenReturn(List.of(entityBinding));
        when(entityMapper.selectById("entity-1")).thenReturn(entity);
        when(formMapper.selectByEntityId("entity-1"))
                .thenReturn(List.of(form));
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(
                                bindingSnapshot(entityBinding),
                                bindingSnapshot(formReplace))));

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertEquals("ENTITY_DEFAULT", reference.getInheritanceSource());
        assertEquals("PUBLISHED_MATCH", reference.getLifecycleStatus());
        assertEquals("SHADOWED", reference.getEffectiveStatus());
        assertEquals(Boolean.FALSE, reference.getEffective());
        assertTrue(reference.getEffectiveContexts().isEmpty());
        assertEquals("binding-local", reference.getContexts().get(0)
                .getEffectiveChain().get(0).getBindingId());
        assertEquals("SHADOWED", reference.getContexts().get(0)
                .getEffectiveStatus());
        verify(entityAccessPolicy).requireDynamicById("entity-1");
    }

    @Test
    void reportsOwnerDefaultAsPartialWhenExactTargetReplacesIt() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding entityDefault = binding(
                "binding-entity", "ENTITY", "entity-1", "FIELD_CHANGE",
                step("step-default", "load", "默认字段处理"));
        UiEventBinding fieldReplace = binding(
                "binding-field", "FORM", "form-1", "FIELD_CHANGE",
                step("step-field", "source-local", "handle", "字段覆盖"));
        fieldReplace.setTargetType("FIELD");
        fieldReplace.setTargetKey("status");
        fieldReplace.setInheritanceMode("REPLACE");
        EntityForm form = form("form-1", "release-1");
        UiConfigRelease release = release(
                "release-1", "FORM", "form-1", 5);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString()))
                .thenReturn(List.of(entityDefault));
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(formMapper.selectByEntityId("entity-1"))
                .thenReturn(List.of(form));
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(
                                bindingSnapshot(entityDefault),
                                bindingSnapshot(fieldReplace))));

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertEquals("PUBLISHED_MATCH", reference.getLifecycleStatus());
        assertEquals("PARTIAL", reference.getEffectiveStatus());
        assertNull(reference.getEffective());
        assertEquals("PARTIAL", reference.getContexts().get(0)
                .getEffectiveStatus());
        assertNull(reference.getContexts().get(0)
                .getPublishedStepEffective());
        assertTrue(reference.getContexts().get(0)
                .getEffectiveReason().contains("字段或按钮目标"));
    }

    @Test
    void doesNotGuessPublishedStepAfterDraftWithoutStableStepCodeChanges() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding draft = binding(
                "binding-form", "FORM", "form-1", "FORM_OPEN",
                step(null, "load", "草稿名称"));
        UiEventBinding published = binding(
                "binding-form", "FORM", "form-1", "FORM_OPEN",
                step(null, "load", "线上名称"));
        EntityForm form = form("form-1", "release-1");
        UiConfigRelease release = release(
                "release-1", "FORM", "form-1", 2);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(draft));
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);
        when(releaseMapper.findActiveReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(release));
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(published))));

        List<UiDataSourceReferenceDTO> references =
                service.references("source-1");
        UiDataSourceReferenceDTO reference = references.stream()
                .filter(UiDataSourceReferenceDTO::isDraftPresent)
                .findFirst()
                .orElseThrow();
        UiDataSourceReferenceDTO publishedOnly = references.stream()
                .filter(item -> !item.isDraftPresent())
                .findFirst()
                .orElseThrow();

        assertEquals(2, references.size());
        assertEquals("PUBLISHED_CHANGED", reference.getLifecycleStatus());
        assertFalse(reference.isPublished());
        assertFalse(reference.getContexts().get(0).isDraftStepPublished());
        assertEquals("UNMATCHED", reference.getContexts().get(0)
                .getPublishedMatchBasis());
        assertEquals("DRAFT_STEP_NOT_PUBLISHED",
                reference.getContexts().get(0).getEffectiveStatus());
        assertEquals("PUBLISHED_ONLY", publishedOnly.getLifecycleStatus());
        assertEquals("ACTIVE", publishedOnly.getEffectiveStatus());
        assertTrue(publishedOnly.isPublished());
        assertFalse(reference.getReferenceId().equals(
                publishedOnly.getReferenceId()));
    }

    @Test
    void filtersOwnersThatCurrentUserCannotAccess() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding allowed = binding(
                "binding-allowed", "FORM", "form-1", "FORM_OPEN",
                step("step-1", "load", "加载"));
        UiEventBinding denied = binding(
                "binding-denied", "FORM", "form-denied", "FORM_OPEN",
                step("step-2", "load", "加载"));

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString()))
                .thenReturn(List.of(allowed, denied));
        when(formMapper.selectById("form-1"))
                .thenReturn(form("form-1", null));
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        doThrow(new BusinessForbiddenException(
                "FORM_FORBIDDEN", "无权访问"))
                .when(accessService).requireFormAccess("form-denied");

        List<UiDataSourceReferenceDTO> references =
                service.references("source-1");

        assertEquals(1, references.size());
        assertEquals("binding-allowed", references.get(0).getBindingId());
        assertEquals("DRAFT_ONLY", references.get(0).getLifecycleStatus());
        assertNull(references.get(0).getActiveReleaseVersion());
    }

    @Test
    void reportsListReleasePointerMismatchWithoutReadingSnapshot() {
        UiDataSourceDefinition source = source("LIST");
        UiEventBinding binding = binding(
                "binding-list", "LIST", "list-1", "LIST_LOAD",
                step("step-list", "load", "加载列表"));
        EntityListConfig list = list("list-1", "release-old");
        UiConfigRelease release = release(
                "release-new", "LIST", "list-1", 5);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(binding));
        when(listMapper.selectById("list-1")).thenReturn(list);
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(releaseMapper.findActive("LIST", "list-1"))
                .thenReturn(release);

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertEquals("PUBLISH_STATE_UNAVAILABLE",
                reference.getPublicationStatus());
        assertEquals("UNPUBLISHED", reference.getLifecycleStatus());
        assertEquals("UNKNOWN", reference.getEffectiveStatus());
        assertNull(reference.getEffective());
    }

    @Test
    void reportsFormReleasePointerMismatchWithoutReadingSnapshot() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding binding = binding(
                "binding-form", "FORM", "form-1", "FORM_OPEN",
                step("step-form", "load", "加载表单"));
        EntityForm form = form("form-1", "release-old");
        UiConfigRelease release = release(
                "release-new", "FORM", "form-1", 5);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(binding));
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertEquals("PUBLISH_STATE_UNAVAILABLE",
                reference.getPublicationStatus());
        assertEquals("UNPUBLISHED", reference.getLifecycleStatus());
        assertEquals("UNKNOWN", reference.getEffectiveStatus());
        assertNull(reference.getEffective());
    }

    @Test
    void returnsReferenceThatExistsOnlyInActiveRelease() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding published = binding(
                "binding-published", "FORM", "form-1", "FORM_OPEN",
                step("step-online", "load", "线上加载"));
        EntityForm form = form("form-1", "release-1");
        UiConfigRelease release = release(
                "release-1", "FORM", "form-1", 6);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of());
        when(releaseMapper.findActiveReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(release));
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(published))));

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertFalse(reference.isDraftPresent());
        assertNull(reference.getDraftRevision());
        assertTrue(reference.isPublished());
        assertEquals("PUBLISHED_ONLY", reference.getPublicationStatus());
        assertEquals("PUBLISHED_ONLY", reference.getLifecycleStatus());
        assertEquals("ACTIVE", reference.getEffectiveStatus());
        assertEquals(Boolean.TRUE, reference.getEffective());
        assertEquals("PUBLISHED_ONLY", reference.getContexts().get(0)
                .getPublishedMatchBasis());
    }

    @Test
    void publishedOnlyMixedContextsHaveDistinctReferenceIdsWithoutStepCodes() {
        UiDataSourceDefinition source = new UiDataSourceDefinition();
        source.setId("source-1");
        source.setSourceCode("acceptance_query");
        source.setSourceName("验收查询");
        source.setOperationsDocument(codec.write(
                List.of(
                        Map.of(
                                "code", "formLoad",
                                "name", "表单加载",
                                "kind", "READ",
                                "contextType", "FORM"),
                        Map.of(
                                "code", "listLoad",
                                "name", "列表加载",
                                "kind", "READ",
                                "contextType", "LIST")),
                "测试接口操作"));
        source.setEnabled(true);
        source.setDeleted(0);
        Map<String, Object> formStep = step(
                null, "source-1", "formLoad", "表单加载");
        Map<String, Object> listStep = step(
                null, "source-1", "listLoad", "列表加载");
        UiEventBinding published = binding(
                "binding-entity", "ENTITY", "entity-1", "DETAIL_LOAD",
                List.of(formStep, listStep));
        EntityForm form = form("form-1", "release-form");
        EntityListConfig list = list("list-1", "release-list");
        UiConfigRelease formRelease = release(
                "release-form", "FORM", "form-1", 2);
        UiConfigRelease listRelease = release(
                "release-list", "LIST", "list-1", 3);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of());
        when(releaseMapper.findActiveReferenceCandidates(
                anyString(), anyString()))
                .thenReturn(List.of(formRelease, listRelease));
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(listMapper.selectById("list-1")).thenReturn(list);
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(releaseService.verifiedReleaseSnapshot(formRelease))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(
                                published, List.of(formStep)))));
        when(releaseService.verifiedReleaseSnapshot(listRelease))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(
                                published, List.of(listStep)))));

        List<UiDataSourceReferenceDTO> references =
                service.references("source-1");

        assertEquals(2, references.size());
        assertEquals(2, references.stream()
                .map(UiDataSourceReferenceDTO::getReferenceId)
                .distinct()
                .count());
        assertTrue(references.stream().allMatch(
                item -> "PUBLISHED_ONLY".equals(
                        item.getLifecycleStatus())));
    }

    @Test
    void mixedEntityStepsWithoutStepCodesMatchEachProjectedSnapshot() {
        UiDataSourceDefinition listSource = source(
                "source-list", "listLoad", "LIST");
        UiDataSourceDefinition formSource = source(
                "source-form", "formLoad", "FORM");
        Map<String, Object> listStep = step(
                null, "source-list", "listLoad", "列表加载");
        Map<String, Object> formStep = step(
                null, "source-form", "formLoad", "表单加载");
        UiEventBinding draft = binding(
                "binding-entity", "ENTITY", "entity-1", "DETAIL_LOAD",
                List.of(listStep, formStep));
        EntityForm form = form("form-1", "release-form");
        EntityListConfig list = list("list-1", "release-list");
        UiConfigRelease formRelease = release(
                "release-form", "FORM", "form-1", 2);
        UiConfigRelease listRelease = release(
                "release-list", "LIST", "list-1", 3);

        when(dataSourceMapper.selectById("source-list"))
                .thenReturn(listSource);
        when(dataSourceMapper.selectById("source-form"))
                .thenReturn(formSource);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(draft));
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(formMapper.selectByEntityId("entity-1"))
                .thenReturn(List.of(form));
        when(listMapper.findByEntityId("entity-1"))
                .thenReturn(List.of(list));
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(formRelease);
        when(releaseMapper.findActive("LIST", "list-1"))
                .thenReturn(listRelease);
        when(releaseService.verifiedReleaseSnapshot(formRelease))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(draft, List.of(formStep)))));
        when(releaseService.verifiedReleaseSnapshot(listRelease))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(draft, List.of(listStep)))));

        UiDataSourceReferenceDTO formReference =
                service.references("source-form").get(0);
        UiDataSourceReferenceDTO listReference =
                service.references("source-list").get(0);

        assertEquals("PUBLISHED_MATCH", formReference.getLifecycleStatus());
        assertEquals("ACTIVE", formReference.getEffectiveStatus());
        assertEquals(1, formReference.getContexts().size());
        assertEquals("FORM", formReference.getContexts().get(0)
                .getConfigType());
        assertEquals("EXACT_BINDING_INDEX", formReference.getContexts().get(0)
                .getPublishedMatchBasis());
        assertEquals("PUBLISHED_MATCH", listReference.getLifecycleStatus());
        assertEquals("ACTIVE", listReference.getEffectiveStatus());
        assertEquals(1, listReference.getContexts().size());
        assertEquals("LIST", listReference.getContexts().get(0)
                .getConfigType());
    }

    @Test
    void legacyEntityOperationCannotBorrowLaterProjectedStepIndex() {
        UiDataSourceDefinition legacySource = source(
                "source-legacy", "legacyLoad", "ENTITY");
        UiDataSourceDefinition formSource = source(
                "source-form", "formLoad", "FORM");
        Map<String, Object> legacyStep = step(
                null, "source-legacy", "legacyLoad", "旧实体操作");
        Map<String, Object> formStep = step(
                null, "source-form", "formLoad", "表单加载");
        UiEventBinding draft = binding(
                "binding-entity", "ENTITY", "entity-1", "DETAIL_LOAD",
                List.of(legacyStep, formStep));
        EntityForm form = form("form-1", "release-form");
        UiConfigRelease release = release(
                "release-form", "FORM", "form-1", 4);

        when(dataSourceMapper.selectById("source-legacy"))
                .thenReturn(legacySource);
        when(dataSourceMapper.selectById("source-form"))
                .thenReturn(formSource);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(draft));
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(formMapper.selectByEntityId("entity-1"))
                .thenReturn(List.of(form));
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(draft, List.of(formStep)))));

        UiDataSourceReferenceDTO reference =
                service.references("source-legacy").get(0);

        assertEquals("PUBLISHED_CHANGED", reference.getLifecycleStatus());
        assertFalse(reference.isPublished());
        assertFalse(reference.getContexts().get(0).isDraftStepPublished());
        assertEquals("UNMATCHED", reference.getContexts().get(0)
                .getPublishedMatchBasis());
        assertEquals("DRAFT_STEP_NOT_PUBLISHED",
                reference.getEffectiveStatus());
    }

    @Test
    void preservesOnlineEffectiveStateWhenDraftBindingIsDisabled() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding draft = binding(
                "binding-form", "FORM", "form-1", "FORM_OPEN",
                step("stable-step", "load", "加载"));
        draft.setEnabled(false);
        UiEventBinding published = binding(
                "binding-form", "FORM", "form-1", "FORM_OPEN",
                step("stable-step", "load", "加载"));
        EntityForm form = form("form-1", "release-1");
        UiConfigRelease release = release(
                "release-1", "FORM", "form-1", 7);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(draft));
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(bindingSnapshot(published))));

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertFalse(reference.isBindingEnabled());
        assertEquals("PUBLISHED_CHANGED", reference.getLifecycleStatus());
        assertEquals("PUBLISHED_VERSION_ACTIVE",
                reference.getEffectiveStatus());
        assertEquals(Boolean.TRUE, reference.getEffective());
        assertEquals(Boolean.FALSE, reference.getContexts().get(0)
                .getDraftStepEffective());
        assertEquals(Boolean.TRUE, reference.getContexts().get(0)
                .getPublishedStepEffective());
    }

    @Test
    void reportsDisabledBindingAsMatchedAfterReleaseOmitsIt() {
        UiDataSourceDefinition source = source("FORM");
        UiEventBinding draft = binding(
                "binding-form", "FORM", "form-1", "FORM_OPEN",
                step("stable-step", "load", "加载"));
        draft.setEnabled(false);
        EntityForm form = form("form-1", "release-1");
        UiConfigRelease release = release(
                "release-1", "FORM", "form-1", 8);

        when(dataSourceMapper.selectById("source-1"))
                .thenReturn(source);
        when(bindingMapper.findDraftReferenceCandidates(
                anyString(), anyString())).thenReturn(List.of(draft));
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(entityMapper.selectById("entity-1")).thenReturn(entity());
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of("eventBindings", List.of()));

        UiDataSourceReferenceDTO reference =
                service.references("source-1").get(0);

        assertFalse(reference.isBindingEnabled());
        assertEquals("PUBLISHED_MATCH", reference.getLifecycleStatus());
        assertEquals("PUBLISHED_MATCH", reference.getPublicationStatus());
        assertFalse(reference.isPublished());
        assertEquals(Boolean.TRUE, reference.getDraftMatchesPublished());
        assertEquals("DISABLED", reference.getEffectiveStatus());
        assertEquals(Boolean.FALSE, reference.getEffective());
        assertEquals("DISABLED_ABSENCE", reference.getContexts().get(0)
                .getPublishedMatchBasis());
    }

    private UiDataSourceDefinition source(String contextType) {
        return source("source-1", "load", contextType);
    }

    private UiDataSourceDefinition source(
            String id,
            String operationCode,
            String contextType) {
        UiDataSourceDefinition source = new UiDataSourceDefinition();
        source.setId(id);
        source.setSourceCode("acceptance_query");
        source.setSourceName("验收查询");
        source.setOperationsDocument(codec.write(
                List.of(Map.of(
                        "code", operationCode,
                        "name", "加载",
                        "kind", "READ",
                        "contextType", contextType)),
                "测试接口操作"));
        source.setEnabled(true);
        source.setDeleted(0);
        return source;
    }

    private UiEventBinding binding(
            String id,
            String ownerType,
            String ownerId,
            String eventCode,
            Map<String, Object> step) {
        UiEventBinding binding = new UiEventBinding();
        binding.setId(id);
        binding.setOwnerType(ownerType);
        binding.setOwnerId(ownerId);
        binding.setTargetType("OWNER");
        binding.setTargetKey("");
        binding.setEventCode(eventCode);
        binding.setInheritanceMode("INHERIT");
        binding.setStepsDocument(codec.write(
                List.of(step), "测试事件步骤"));
        binding.setRevision(2);
        binding.setEnabled(true);
        binding.setDeleted(0);
        return binding;
    }

    private UiEventBinding binding(
            String id,
            String ownerType,
            String ownerId,
            String eventCode,
            List<Map<String, Object>> steps) {
        UiEventBinding binding = binding(
                id, ownerType, ownerId, eventCode, steps.get(0));
        binding.setStepsDocument(codec.write(steps, "测试事件步骤"));
        return binding;
    }

    private Map<String, Object> step(
            String stepCode,
            String operationCode,
            String name) {
        return step(
                stepCode, "source-1", operationCode, name);
    }

    private Map<String, Object> step(
            String stepCode,
            String serviceId,
            String operationCode,
            String name) {
        Map<String, Object> step = new LinkedHashMap<>();
        if (stepCode != null) {
            step.put("stepCode", stepCode);
        }
        step.put("name", name);
        step.put("strategy", "BEFORE");
        step.put("serviceId", serviceId);
        step.put("operationCode", operationCode);
        step.put("order", 10);
        step.put("failurePolicy", "STOP");
        return step;
    }

    private Map<String, Object> bindingSnapshot(
            UiEventBinding binding) {
        return bindingSnapshot(
                binding,
                codec.readArray(
                                binding.getStepsDocument(),
                                "测试事件步骤")
                        .stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(this::stringMap)
                        .toList());
    }

    private Map<String, Object> bindingSnapshot(
            UiEventBinding binding,
            List<Map<String, Object>> steps) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", binding.getId());
        result.put("ownerType", binding.getOwnerType());
        result.put("ownerId", binding.getOwnerId());
        result.put("targetType", binding.getTargetType());
        result.put("targetKey", binding.getTargetKey());
        result.put("eventCode", binding.getEventCode());
        result.put("inheritanceMode", binding.getInheritanceMode());
        result.put("steps", steps);
        result.put("revision", binding.getRevision());
        return result;
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private EntityDefinition entity() {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("acceptance");
        entity.setEntityName("项目验收");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        return entity;
    }

    private EntityForm form(String id, String releaseId) {
        EntityForm form = new EntityForm();
        form.setId(id);
        form.setEntityId("entity-1");
        form.setFormKey("default");
        form.setFormName("默认表单");
        form.setActiveReleaseId(releaseId);
        return form;
    }

    private EntityListConfig list(String id, String releaseId) {
        EntityListConfig list = new EntityListConfig();
        list.setId(id);
        list.setEntityId("entity-1");
        list.setEntityCode("acceptance");
        list.setListKey("default");
        list.setListName("默认列表");
        list.setActiveReleaseId(releaseId);
        return list;
    }

    private UiConfigRelease release(
            String id,
            String type,
            String configId,
            int version) {
        UiConfigRelease release = new UiConfigRelease();
        release.setId(id);
        release.setConfigType(type);
        release.setConfigId(configId);
        release.setVersion(version);
        release.setStatus("ACTIVE");
        return release;
    }
}
