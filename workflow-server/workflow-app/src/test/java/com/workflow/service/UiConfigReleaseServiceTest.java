package com.workflow.service;

import com.workflow.entity.form.application.EntityFormNodeService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiConfigSemanticPatchService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiExtensionDefinitionService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessImpact;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessImpactPort;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessTarget;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.ui.api.response.UiConfigDiffDTO;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixTarget;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateVersionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigHotfixTargetMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseAuditMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UI 配置发布服务测试。
 *
 * <p>被测对象：{@link UiConfigReleaseService}，覆盖草稿与发布快照的差分比较、
 * 发布激活时的完整性校验、节点结构校验、跨表单嵌套校验、模板兼容性校验等场景。
 */
class UiConfigReleaseServiceTest {

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
                mock(UiDataSourceDefinitionMapper.class),
                mock(UiComponentTemplateMapper.class),
                mock(UiComponentTemplateVersionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                formService,
                mock(EntityFormNodeService.class),
                mock(UiExtensionDefinitionService.class),
                mock(EntityListConfigService.class),
                mock(EntityListConfigurationValidator.class),
                new UiConfigSemanticPatchService(codec),
                mock(UiHotfixProcessImpactPort.class),
                mock(UiConfigurationAccessService.class),
                mock(UiReleaseResolutionTokenService.class),
                mock(FormSubmissionTraceService.class),
                codec,
                objectMapper);

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

        UiConfigRelease release = new UiConfigRelease();
        release.setSnapshotDocument(codec.write(legacySnapshot, "测试历史发布快照"));
        release.setContentHash("legacy-integrity-hash");
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
                "{\"FORM_INIT\":{\"sourceId\":\"source-init\"}}",
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

        UiConfigRelease release = new UiConfigRelease();
        release.setSnapshotDocument(context.codec().write(
                activeSnapshot,
                "测试历史发布快照"));
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

        UiConfigRelease active = new UiConfigRelease();
        active.setSnapshotDocument(context.codec().write(
                activeSnapshot, "测试移动节点发布快照"));
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
    @SuppressWarnings("unchecked")
    void listHighRiskHotfixIsReviewAndPublishableWithoutOverride() {
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

        UiConfigPublishPreviewDTO preview =
                context.service().publishPreview(
                        UiConfigReleaseService.LIST,
                        "list-1",
                        request);

        assertEquals(
                UiConfigSemanticPatchService.REVIEW,
                preview.getRiskLevel());
        assertFalse(preview.isRequiresOverride());
        assertTrue(preview.isCanPublish());
        assertTrue(preview.getBlockers().isEmpty());
        assertTrue(preview.getRiskItems().stream().anyMatch(item ->
                "/viewConfig/search/defaultVisibleCount".equals(
                        item.getPath())
                        && UiConfigSemanticPatchService.REVIEW.equals(
                                item.getRiskLevel())));
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
                mock(UiDataSourceDefinitionMapper.class),
                mock(UiComponentTemplateMapper.class),
                mock(UiComponentTemplateVersionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityFormService.class),
                mock(EntityFormNodeService.class),
                mock(UiExtensionDefinitionService.class),
                listConfigService,
                mock(EntityListConfigurationValidator.class),
                new UiConfigSemanticPatchService(codec),
                mock(UiHotfixProcessImpactPort.class),
                mock(UiConfigurationAccessService.class),
                mock(UiReleaseResolutionTokenService.class),
                mock(FormSubmissionTraceService.class),
                codec,
                objectMapper);

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
     * 验证抛出 IllegalArgumentException 且消息包含"与节点类型 FIELD 不兼容"。
     */
    @Test
    void rejectsActivationWhenTemplateTypeIsIncompatible() {
        TestContext context = context();
        Map<String, Object> field = node("field", null, "FIELD");
        field.put("templateId", "subform-template");
        field.put("templateVersion", 1);
        UiConfigRelease release = release(
                context.codec(),
                "release-1",
                formSnapshot(List.of(field)));
        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("subform-template");
        template.setTemplateType("SUB_FORM");
        template.setStatus("ACTIVE");
        template.setDeleted(0);
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);
        when(context.templateMapper().selectById("subform-template"))
                .thenReturn(template);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().activate("FORM", "form-1", "release-1"));

        assertTrue(exception.getMessage().contains("与节点类型 FIELD 不兼容"));
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
        UiHotfixProcessImpactPort processImpactPort =
                mock(UiHotfixProcessImpactPort.class);
        when(formMapper.selectByIdForUpdate("form-1"))
                .thenReturn(form());
        UiConfigReleaseService service = new UiConfigReleaseService(
                releaseMapper,
                hotfixTargetMapper,
                mock(UiConfigReleaseAuditMapper.class),
                mock(UiDataSourceDefinitionMapper.class),
                templateMapper,
                templateVersionMapper,
                formMapper,
                mock(EntityListConfigMapper.class),
                formService,
                mock(EntityFormNodeService.class),
                mock(UiExtensionDefinitionService.class),
                listConfigService,
                mock(EntityListConfigurationValidator.class),
                new UiConfigSemanticPatchService(codec),
                processImpactPort,
                mock(UiConfigurationAccessService.class),
                mock(UiReleaseResolutionTokenService.class),
                mock(FormSubmissionTraceService.class),
                codec,
                objectMapper);
        return new TestContext(
                service,
                releaseMapper,
                hotfixTargetMapper,
                templateMapper,
                templateVersionMapper,
                formService,
                listConfigService,
                processImpactPort,
                codec);
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
        list.setToolbarConfig(List.of());
        list.setRowActionConfig(List.of());
        return list;
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
            UiHotfixProcessImpactPort processImpactPort,
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
                "{\"FORM_INIT\":{\"sourceId\":\"source-init\"}}");

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
