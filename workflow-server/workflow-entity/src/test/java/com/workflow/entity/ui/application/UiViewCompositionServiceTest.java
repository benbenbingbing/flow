package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiViewCompositionSaveRequest;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionTestDTO;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiViewCompositionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiViewComposition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiViewCompositionServiceTest {

    private static final Pattern REVISION_PARAMETER = Pattern.compile(
            "WHERE.*revision\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}",
            Pattern.CASE_INSENSITIVE);

    @Test
    void actionOnlySpecialHandlingDoesNotReplaceStandardDataResolution() {
        Fixture fixture = fixture();
        boolean result = ReflectionTestUtils.invokeMethod(
                fixture.service,
                "usesInterfaceService",
                Map.of("type", "REVERSE_REFERENCE"),
                Map.of(
                        "mode", "INTERFACE_SERVICE",
                        "interfaceService", Map.of(),
                        "actionServices", List.of(Map.of(
                                "actionKey", "CHECK_RISK"))));

        assertFalse(result);
    }

    @Test
    void listButtonsReferenceOnlyEnabledPopupContentInTheSameSnapshot() {
        Fixture fixture = fixture();
        Map<String, Object> button = new LinkedHashMap<>(Map.of(
                "key", "requirements", "label", "需求", "type", "custom",
                "customMode", "open-related-content", "compositionKey", "requirements"));
        Map<String, Object> config = new LinkedHashMap<>(Map.of(
                "enabled", true, "presentation", Map.of("position", "DRAWER")));
        Map<String, Object> item = new LinkedHashMap<>(Map.of(
                "compositionKey", "requirements", "anchorType", "LIST_ACTION", "config", config));
        for (String anchor : List.of("LIST_ACTION", "ROW_ACTION", "TOOLBAR_ACTION")) {
            item.put("anchorType", anchor);
            // 发布快照的按钮既可能是 JSON 文档，也可能已被归一为数组。
            Map<String, Object> owner = Map.of(
                    "toolbarConfig", fixture.codec.write(List.of(button), "按钮"),
                    "rowActionConfig", List.of(button));
            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(fixture.service,
                    "validateListButtonReferences", owner, List.of(item)));
        }
        Map<String, Object> owner = Map.of("toolbarConfig", List.of(button));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.validateReleaseSnapshot("LIST", "list-1", Map.of(
                        "configType", "LIST", "list", Map.of(
                                "id", "list-1", "entityId", "entity-1", "toolbarConfig", List.of(button)))));
        assertTrue(missing.getMessage().contains("引用的关联内容不存在"));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                fixture.service, "validateListButtonReferences", owner, List.of()));
        config.put("enabled", false);
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                fixture.service, "validateListButtonReferences", owner, List.of(item)));
        config.put("enabled", true);
        config.put("presentation", Map.of("position", "INLINE"));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                fixture.service, "validateListButtonReferences", owner, List.of(item)));
        button.put("enabled", false);
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(fixture.service,
                "validateListButtonReferences", owner, List.of()));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(fixture.service,
                "validateAnchorPlacement", "LIST", "LIST_ACTION",
                Map.of("presentation", Map.of("position", "DIALOG"))));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                fixture.service, "validateAnchorPlacement", "FORM", "LIST_ACTION",
                Map.of("presentation", Map.of("position", "DIALOG"))));
    }

    @Test
    void createBumpsOwnerRevisionAndClearsDraftHash() {
        Fixture fixture = fixture();
        AtomicReference<UiViewComposition> inserted = new AtomicReference<>();
        when(fixture.mapper.insert(any(UiViewComposition.class)))
                .thenAnswer(invocation -> {
            UiViewComposition value = invocation.getArgument(0);
            value.setId("composition-1");
            inserted.set(value);
            return 1;
        });
        when(fixture.mapper.selectById(anyString()))
                .thenAnswer(invocation -> inserted.get());
        when(fixture.formMapper.update(isNull(), any())).thenReturn(1);

        UiViewCompositionDTO result = fixture.service.create(
                "FORM", "form-1", request(null));

        assertEquals(1, result.getRevision());
        assertEquals(2, result.getOwnerRevision());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<EntityForm>> ownerUpdate =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(fixture.formMapper).update(isNull(), ownerUpdate.capture());
        assertTrue(ownerUpdate.getValue().getSqlSet().contains("draft_hash"));
        assertTrue(ownerUpdate.getValue().getSqlSet().contains("revision"));
    }

    @Test
    void updateMatchesPersistedCompositionRevisionBeforeIncrementing() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 3);
        when(fixture.mapper.selectByIdForUpdate("composition-1"))
                .thenReturn(current);
        when(fixture.mapper.findActiveByKey(
                "FORM", "form-1", "project_requirements"))
                .thenReturn(current);
        when(fixture.mapper.update(isNull(), any())).thenReturn(1);
        UiViewComposition saved = existing(fixture.codec, 4);
        when(fixture.mapper.selectById("composition-1")).thenReturn(saved);
        when(fixture.formMapper.update(isNull(), any())).thenReturn(1);

        UiViewCompositionDTO result = fixture.service.update(
                "FORM", "form-1", "composition-1", request(3));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UiViewComposition>> updateCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(fixture.mapper).update(isNull(), updateCaptor.capture());
        UpdateWrapper<UiViewComposition> update = updateCaptor.getValue();
        Matcher matcher = REVISION_PARAMETER.matcher(
                update.getCustomSqlSegment());
        assertTrue(matcher.find(), update::getCustomSqlSegment);
        assertEquals(3,
                update.getParamNameValuePairs().get(matcher.group(1)));
        assertEquals(4, result.getRevision());
        assertEquals(2, result.getOwnerRevision());
    }

    @Test
    void legacyInterfaceDraftLoadsAndSavesAsExtensionIdOnly() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 3);
        current.setConfigDocument(fixture.codec.write(
                legacyInterfaceConfig(), "legacy interface config"));
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));
        UiExtensionDefinition migrated = interfaceDefinition(
                "extension-1", "ACTIVE");
        when(fixture.dataSourceService.resolveDefinitionReference(
                "legacy-service", "resolveRequirements"))
                .thenReturn(migrated);

        UiViewCompositionDTO loaded = fixture.service.list(
                "FORM", "form-1").get(0);
        Map<?, ?> loadedSpecial = (Map<?, ?>) loaded.getConfig().get(
                "specialHandling");
        Map<?, ?> loadedBinding = (Map<?, ?>) loadedSpecial.get(
                "interfaceService");
        assertEquals("extension-1", loadedBinding.get("extensionId"));
        assertFalse(loadedBinding.containsKey("serviceId"));
        assertFalse(loadedBinding.containsKey("operationCode"));

        when(fixture.mapper.selectByIdForUpdate("composition-1"))
                .thenReturn(current);
        when(fixture.mapper.findActiveByKey(
                "FORM", "form-1", "project_requirements"))
                .thenReturn(current);
        when(fixture.mapper.update(isNull(), any())).thenReturn(1);
        when(fixture.formMapper.update(isNull(), any())).thenReturn(1);
        UiViewComposition saved = existing(fixture.codec, 4);
        saved.setConfigDocument(fixture.codec.write(
                loaded.getConfig(), "saved interface config"));
        when(fixture.mapper.selectById("composition-1")).thenReturn(saved);
        when(fixture.dataSourceService.requireExecutableDefinition(
                "extension-1", null)).thenReturn(migrated);
        UiViewCompositionSaveRequest update = request(3);
        update.setConfig(loaded.getConfig());

        fixture.service.update(
                "FORM", "form-1", "composition-1", update);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UiViewComposition>> updateCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(fixture.mapper).update(isNull(), updateCaptor.capture());
        String savedDocument = updateCaptor.getValue()
                .getParamNameValuePairs().values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> value.contains("specialHandling"))
                .findFirst()
                .orElseThrow();
        Map<String, Object> persisted = fixture.codec.readObject(
                savedDocument, "persisted interface config");
        Map<?, ?> persistedBinding = (Map<?, ?>) ((Map<?, ?>) persisted.get(
                "specialHandling")).get("interfaceService");
        assertEquals("extension-1", persistedBinding.get("extensionId"));
        assertFalse(persistedBinding.containsKey("serviceId"));
        assertFalse(persistedBinding.containsKey("operationCode"));
    }

    @Test
    void restoreNormalizesDisabledLegacyInterfaceWithoutRequiringExecution() {
        Fixture fixture = fixture();
        UiExtensionDefinition disabled = interfaceDefinition(
                "extension-1", "DISABLED");
        when(fixture.dataSourceService.resolveDefinitionReference(
                "legacy-service", "resolveRequirements"))
                .thenReturn(disabled);

        fixture.service.restoreForRelease(
                "FORM",
                "form-1",
                List.of(Map.of(
                        "id", "composition-1",
                        "compositionKey", "project_requirements",
                        "anchorType", "FORM_END",
                        "orderKey", 1000,
                        "config", legacyInterfaceConfig())));

        ArgumentCaptor<UiViewComposition> captor =
                ArgumentCaptor.forClass(UiViewComposition.class);
        verify(fixture.mapper).insert(captor.capture());
        Map<String, Object> restored = fixture.codec.readObject(
                captor.getValue().getConfigDocument(), "restored config");
        Map<?, ?> binding = (Map<?, ?>) ((Map<?, ?>) restored.get(
                "specialHandling")).get("interfaceService");
        assertEquals("extension-1", binding.get("extensionId"));
        assertFalse(binding.containsKey("serviceId"));
        assertFalse(binding.containsKey("operationCode"));
        verify(fixture.dataSourceService, never())
                .requireExecutableDefinition(anyString(), any());
    }

    @Test
    void updateRejectsStaleOwnerRevisionAfterDraftRestore() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 1);
        when(fixture.mapper.selectByIdForUpdate("composition-1"))
                .thenReturn(current);
        UiViewCompositionSaveRequest request = request(1);
        request.setExpectedOwnerRevision(0);

        assertThrows(RevisionConflictException.class,
                () -> fixture.service.update(
                "FORM", "form-1", "composition-1", request));
    }

    @Test
    void snapshotAndProjectedSnapshotPinTheCurrentTargetRelease() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 9);
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));

        List<Map<String, Object>> snapshot = fixture.service.snapshot(
                "FORM", "form-1");
        Map<String, Object> item = snapshot.get(0);
        assertTrue(!item.containsKey("revision"));
        Map<?, ?> target = (Map<?, ?>) ((Map<?, ?>) item.get("config"))
                .get("target");
        assertEquals("release-target", target.get("releaseId"));
        assertEquals(7, target.get("releaseVersion"));
        Map<?, ?> entitySnapshots = (Map<?, ?>) ((Map<?, ?>) item.get(
                "config")).get("entitySnapshots");
        assertEquals(
                "project-history",
                ((Map<?, ?>) entitySnapshots.get("source")).get("historyId"));
        assertEquals(
                "requirement-history",
                ((Map<?, ?>) entitySnapshots.get("target")).get("historyId"));

        List<Map<String, Object>> projected = fixture.service
                .normalizeSnapshotForCurrentDependencies(snapshot);
        assertEquals(snapshot, projected);
    }

    @Test
    void releaseSnapshotValidatesStructureAnchorAndPinnedTargetHash() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 9);
        current.setAnchorType("FORM_NODE");
        current.setAnchorKey("requirements_tab");
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));
        List<Map<String, Object>> compositions = fixture.service.snapshot(
                "FORM", "form-1");
        Map<String, Object> ownerSnapshot = ownerSnapshot(
                compositions,
                List.of(Map.of(
                        "id", "node-1",
                        "nodeKey", "requirements_tab",
                        "nodeType", "SECTION")));

        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", ownerSnapshot));
        verify(fixture.containmentGuard).validate(
                "FORM", "form-1", ownerSnapshot);

        Map<String, Object> missingAnchorSnapshot = ownerSnapshot(
                compositions,
                List.of(Map.of(
                        "id", "other-node",
                        "nodeKey", "other_node")));
        IllegalArgumentException anchorFailure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", missingAnchorSnapshot));
        assertTrue(anchorFailure.getMessage().contains("挂载的表单节点不存在"));

        Map<String, Object> wrongTab = fixture.codec.readObject(
                fixture.codec.write(ownerSnapshot, "test"), "test");
        @SuppressWarnings("unchecked")
        Map<String, Object> wrongTabConfig = (Map<String, Object>)
                ((Map<?, ?>) ((List<?>) wrongTab.get(
                        "viewCompositions")).get(0)).get("config");
        wrongTabConfig.put("presentation", Map.of(
                "position", "TAB", "loadMode", "ON_DEMAND"));
        IllegalArgumentException tabFailure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", wrongTab));
        assertTrue(tabFailure.getMessage().contains(
                "放置位置必须是表单中的 Tab 页"));

        @SuppressWarnings("unchecked")
        Map<String, Object> tabNode = (Map<String, Object>)
                ((List<?>) wrongTab.get("nodes")).get(0);
        tabNode.put("nodeType", "TAB");
        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", wrongTab));

        Map<String, Object> tampered = fixture.codec.readObject(
                fixture.codec.write(ownerSnapshot, "test"), "test");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) (
                (Map<?, ?>) ((List<?>) tampered.get(
                        "viewCompositions")).get(0)).get("config");
        @SuppressWarnings("unchecked")
        Map<String, Object> target =
                (Map<String, Object>) config.get("target");
        target.put("contentHash", "b".repeat(64));
        BusinessConflictException hashFailure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", tampered));
        assertEquals(
                "UI_VIEW_COMPOSITION_TARGET_RELEASE_CONFLICT",
                hashFailure.getErrorCode());
    }

    @Test
    void releaseSnapshotValidatesSelectMappingsAgainstBothPublishedAssets() {
        Fixture fixture = fixture();
        Map<String, Object> config = new LinkedHashMap<>(config());
        config.put("actions", List.of("VIEW", "SELECT"));
        config.put("actionSettings", Map.of(
                "select", Map.of(
                        "mode", "SINGLE",
                        "result", "FILL_FIELDS",
                        "mappings", List.of(Map.of(
                                "sourceField", "requirementName",
                                "targetField", "selectedRequirementName",
                                "required", true))),
                "create", Map.of(
                        "associateAfterCreate", false,
                        "initialMappings", List.of())));
        UiViewComposition composition = existing(fixture.codec, 1);
        composition.setConfigDocument(fixture.codec.write(config, "test"));
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(composition));

        Map<String, Object> owner = ownerSnapshot(
                fixture.service.snapshot("FORM", "form-1"), List.of());
        owner.put("legacyFields", List.of(Map.of(
                "fieldCode", "selectedRequirementName",
                "isHidden", 0,
                "isReadonly", 0)));
        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", owner));

        Map<String, Object> readonlyOwner = fixture.codec.readObject(
                fixture.codec.write(owner, "test"), "test");
        @SuppressWarnings("unchecked")
        Map<String, Object> ownerField = (Map<String, Object>)
                ((List<?>) readonlyOwner.get("legacyFields")).get(0);
        ownerField.put("isReadonly", 1);
        IllegalArgumentException readonlyFailure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", readonlyOwner));
        assertTrue(readonlyFailure.getMessage().contains(
                "当前字段未在固定表单版本的“编辑”模式中开放"));
        assertTrue(readonlyFailure.getMessage().contains(
                "返回第三步“允许做什么”"));
    }

    @Test
    void releaseSnapshotRejectsSelectMappingFromHiddenTargetListField() {
        Fixture fixture = fixture();
        updateTargetRelease(
                fixture,
                "LIST",
                "list-target",
                targetListReleaseDocument(false));
        Map<String, Object> config = new LinkedHashMap<>(config());
        config.put("actions", List.of("VIEW", "SELECT"));
        config.put("actionSettings", Map.of(
                "select", Map.of(
                        "mode", "SINGLE",
                        "result", "FILL_FIELDS",
                        "mappings", List.of(Map.of(
                                "sourceField", "requirementName",
                                "targetField", "selectedRequirementName"))),
                "create", Map.of(
                        "associateAfterCreate", false,
                        "initialMappings", List.of())));
        UiViewComposition composition = existing(fixture.codec, 1);
        composition.setConfigDocument(fixture.codec.write(config, "test"));
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(composition));
        Map<String, Object> owner = ownerSnapshot(
                fixture.service.snapshot("FORM", "form-1"), List.of());
        owner.put("legacyFields", List.of(Map.of(
                "fieldCode", "selectedRequirementName",
                "isHidden", 0,
                "isReadonly", 0)));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", owner));
        assertTrue(failure.getMessage().contains(
                "目标字段未在固定列表版本中显示"));
    }

    @Test
    void releaseSnapshotValidatesCreateInitialMappingsAgainstPinnedForm() {
        Fixture fixture = fixture();
        EntityForm targetForm = new EntityForm();
        targetForm.setId("form-target");
        targetForm.setEntityId("requirement-entity");
        targetForm.setStatus(1);
        targetForm.setActiveReleaseId("release-target");
        when(fixture.formMapper.selectById("form-target"))
                .thenReturn(targetForm);
        updateTargetRelease(
                fixture,
                "FORM",
                "form-target",
                targetFormReleaseDocument(false));

        Map<String, Object> config = new LinkedHashMap<>(config());
        config.put("target", Map.of(
                "entityId", "requirement-entity",
                "entityCode", "requirement",
                "entityName", "需求",
                "contentType", "FORM",
                "contentId", "form-target",
                "contentKey", "requirement_detail",
                "contentName", "需求详情"));
        config.put("actions", List.of("VIEW", "CREATE"));
        config.put("actionSettings", Map.of(
                "select", Map.of(
                        "mode", "SINGLE",
                        "result", "FILL_FIELDS",
                        "mappings", List.of()),
                "create", Map.of(
                        "associateAfterCreate", false,
                        "initialMappings", List.of(Map.of(
                                "sourceField", "projectCode",
                                "targetField", "requirementProjectCode")))));
        UiViewComposition composition = existing(fixture.codec, 1);
        composition.setConfigDocument(fixture.codec.write(config, "test"));
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(composition));
        Map<String, Object> owner = ownerSnapshot(
                fixture.service.snapshot("FORM", "form-1"), List.of());
        owner.put("legacyFields", List.of(Map.of(
                "fieldCode", "projectCode",
                "isHidden", 0,
                "isReadonly", 1)));

        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", owner));

        updateTargetRelease(
                fixture,
                "FORM",
                "form-target",
                targetFormReleaseDocument(true));
        // 重新生成宿主快照，使目标哈希也固定到这个只读目标表单版本。
        Map<String, Object> invalidOwner = ownerSnapshot(
                fixture.service.snapshot("FORM", "form-1"), List.of());
        invalidOwner.put("legacyFields", owner.get("legacyFields"));
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", invalidOwner));
        assertTrue(failure.getMessage().contains(
                "目标字段未在固定表单版本的“新增”模式中开放"));
    }

    @Test
    void releaseSnapshotRejectsFormNodeAnchorForFullPageCustomForm() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 9);
        current.setAnchorType("FORM_NODE");
        current.setAnchorKey("requirements_section");
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));
        List<Map<String, Object>> compositions = fixture.service.snapshot(
                "FORM", "form-1");
        Map<String, Object> customFormSnapshot = ownerSnapshot(
                compositions,
                List.of(Map.of(
                        "id", "node-1",
                        "nodeKey", "requirements_section",
                        "nodeType", "SECTION")),
                "project-custom-form");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", customFormSnapshot));

        assertTrue(failure.getMessage().contains("整页自定义表单组件"));
        assertTrue(failure.getMessage().contains("请改用表单宿主入口"));

        Map<String, Object> ownerAnchorSnapshot = fixture.codec.readObject(
                fixture.codec.write(customFormSnapshot, "test"), "test");
        @SuppressWarnings("unchecked")
        Map<String, Object> ownerAnchor = (Map<String, Object>)
                ((List<?>) ownerAnchorSnapshot.get("viewCompositions")).get(0);
        ownerAnchor.put("anchorType", "OWNER");
        ownerAnchor.remove("anchorKey");
        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", ownerAnchorSnapshot));
    }

    @Test
    void releaseSnapshotOnlyAllowsPageSectionForFullPageCustomList() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 9);
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));
        Map<String, Object> pageSection = listOwnerSnapshot(
                fixture.service.snapshot("FORM", "form-1"),
                "project-custom-list");

        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "LIST", "list-1", pageSection));

        Map<String, String> blockedAnchors = Map.of(
                "ROW_EXPAND", "ROW_EXPAND",
                "ROW_ACTION", "DRAWER",
                "TOOLBAR_ACTION", "DRAWER");
        blockedAnchors.forEach((anchorType, position) -> {
            Map<String, Object> blocked = fixture.codec.readObject(
                    fixture.codec.write(pageSection, "test"), "test");
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>)
                    ((List<?>) blocked.get("viewCompositions")).get(0);
            item.put("anchorType", anchorType);
            item.put("anchorKey", "custom-list-anchor");
            @SuppressWarnings("unchecked")
            Map<String, Object> itemConfig = (Map<String, Object>)
                    item.get("config");
            itemConfig.put("presentation", Map.of(
                    "position", position,
                    "loadMode", "ON_DEMAND"));

            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> fixture.service.validateReleaseSnapshot(
                            "LIST", "list-1", blocked));
            assertTrue(failure.getMessage().contains("整页自定义列表组件"));
            assertTrue(failure.getMessage().contains("请改用页面区块"));
        });
    }

    @Test
    void releaseSnapshotRejectsTamperedPinnedEntitySchema() {
        Fixture fixture = fixture();
        UiViewComposition current = existing(fixture.codec, 9);
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));
        Map<String, Object> published = ownerSnapshot(
                fixture.service.snapshot("FORM", "form-1"),
                List.of());
        Map<String, Object> tampered = fixture.codec.readObject(
                fixture.codec.write(published, "test"), "test");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) (
                (Map<?, ?>) ((List<?>) tampered.get(
                        "viewCompositions")).get(0)).get("config");
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshots =
                (Map<String, Object>) config.get("entitySnapshots");
        @SuppressWarnings("unchecked")
        Map<String, Object> target =
                (Map<String, Object>) snapshots.get("target");
        target.put("schemaHash", "c".repeat(64));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM", "form-1", tampered));

        assertEquals(
                "UI_VIEW_COMPOSITION_ENTITY_SNAPSHOT_CONFLICT",
                failure.getErrorCode());
    }

    @Test
    void releaseSnapshotKeepsPinnedInterfaceWhenCurrentRevisionDrifts() {
        Fixture fixture = fixture();
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("service-1");
        definition.setSourceCode("projectRequirements");
        definition.setRevision(3);
        definition.setEnabled(true);
        definition.setDeleted(0);
        stubInterface(fixture, "READ");
        String executable = fixture.codec.canonicalize(
                fixture.codec.write(Map.of(
                        "schemaVersion", 1,
                        "id", "service-1"), "pinned operation"),
                "pinned operation");
        when(fixture.dataSourceService.freezeExtension("service-1"))
                .thenReturn(new UiInterfaceExtensionService
                        .PublishedOperationSnapshot(
                                "service-1",
                                "projectRequirements",
                                3,
                                "resolveRequirements",
                                executable,
                                sha256(executable)));
        UiViewComposition current = existing(fixture.codec, 9);
        current.setConfigDocument(fixture.codec.write(
                interfaceConfig(), "test interface config"));
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));
        List<Map<String, Object>> compositions = fixture.service.snapshot(
                "FORM", "form-1");

        definition.setRevision(4);
        assertDoesNotThrow(
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM",
                        "form-1",
                        ownerSnapshot(compositions, List.of())));
        verify(fixture.dataSourceService).validatePinnedReadExtension(
                executable,
                sha256(executable),
                "service-1",
                "projectRequirements",
                3,
                "FORM");
    }

    @Test
    void releaseSnapshotKeepsPinnedComponentWhenRegistryMetadataDrifts() {
        Fixture fixture = fixture();
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setExtensionKey("project-timeline");
        definition.setExtensionType("FORM");
        definition.setVersion(2);
        definition.setSnapshotVersion(5);
        definition.setStatus("ACTIVE");
        when(fixture.extensionService.list(
                null, "project-timeline", "ACTIVE"))
                .thenReturn(List.of(definition));
        UiViewComposition current = existing(fixture.codec, 9);
        current.setConfigDocument(fixture.codec.write(
                componentConfig(), "test component config"));
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(current));
        List<Map<String, Object>> compositions = fixture.service.snapshot(
                "FORM", "form-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> publishedConfig = (Map<String, Object>)
                compositions.get(0).get("config");
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedSpecial = (Map<String, Object>)
                publishedConfig.get("specialHandling");
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedComponent = (Map<String, Object>)
                publishedSpecial.get("customComponent");
        assertEquals("b".repeat(64),
                publishedComponent.get("artifactDigest"));
        Map<String, Object> pinnedDefinition = fixture.codec.readObject(
                String.valueOf(publishedComponent.get("definitionSnapshot")),
                "pinned component definition");
        assertEquals(2, pinnedDefinition.get("schemaVersion"));
        assertEquals("b".repeat(64),
                pinnedDefinition.get("artifactDigest"));

        definition.setSnapshotVersion(6);
        assertDoesNotThrow(
                () -> fixture.service.validateReleaseSnapshot(
                        "FORM",
                        "form-1",
                        ownerSnapshot(compositions, List.of())));
    }

    @Test
    void realDataTestExecutesInterfaceModeWithOnlyMappedSourceFields() {
        Fixture fixture = fixture();
        Map<String, Object> config = interfaceSpecialConfig(
                "INTERFACE_SERVICE", true, false);
        EntityDataDTO source = record(
                "project-1",
                "project",
                Map.of(
                        "ownerId", "user-9",
                        "salary", 999_999));
        when(fixture.entityDataService.findAccessibleById(
                "project", "project-1", null)).thenReturn(source);
        stubInterface(fixture, "READ");
        when(fixture.dataSourceService.previewRelatedContentReadOperation(
                anyString(), any(), any()))
                .thenReturn(Map.of("resolvedProjectId", "project-1"));
        EntityDataDTO target = record(
                "requirement-1", "requirement", Map.of());
        when(fixture.entityDataService.findPage(
                anyString(), anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new PageResult<>(List.of(target), 1, 1, 10));

        UiViewCompositionTestDTO result = fixture.service.test(
                "FORM", "form-1", config, "project-1");

        ArgumentCaptor<UiExtensionExecuteRequest> requestCaptor =
                ArgumentCaptor.forClass(UiExtensionExecuteRequest.class);
        verify(fixture.dataSourceService)
                .previewRelatedContentReadOperation(
                        org.mockito.ArgumentMatchers.eq("service-1"),
                        org.mockito.ArgumentMatchers.isNull(),
                        requestCaptor.capture());
        assertEquals(
                Map.of("criteria", Map.of("ownerId", "user-9")),
                requestCaptor.getValue().getInput());
        assertEquals("RELATED_CONTENT_RESOLVE",
                requestCaptor.getValue().getUsage());
        assertEquals(1, result.getMatchedCount());
        assertEquals(List.of("requirement-1"), result.getTargetRecordIds());
        assertEquals("EQ", result.getFilters().get("projectId_op"));
    }

    @Test
    void realDataTestExecutesInterfaceServiceWhenSpecialModeIsBoth() {
        Fixture fixture = fixture();
        Map<String, Object> config = interfaceSpecialConfig(
                "BOTH", false, true);
        EntityDataDTO source = record(
                "project-1", "project", Map.of("ownerId", "user-9"));
        when(fixture.entityDataService.findAccessibleById(
                "project", "project-1", null)).thenReturn(source);
        stubInterface(fixture, "READ");
        UiExtensionDefinition component = new UiExtensionDefinition();
        component.setExtensionKey("project-timeline");
        component.setExtensionType("FORM");
        component.setVersion(2);
        when(fixture.extensionService.list(
                null, "project-timeline", "ACTIVE"))
                .thenReturn(List.of(component));
        when(fixture.dataSourceService.previewRelatedContentReadOperation(
                anyString(), any(), any()))
                .thenReturn(Map.of("matchNone", true));

        UiViewCompositionTestDTO result = fixture.service.test(
                "FORM", "form-1", config, "project-1");

        assertEquals(0, result.getMatchedCount());
        ArgumentCaptor<UiExtensionExecuteRequest> requestCaptor =
                ArgumentCaptor.forClass(UiExtensionExecuteRequest.class);
        verify(fixture.dataSourceService)
                .previewRelatedContentReadOperation(
                        org.mockito.ArgumentMatchers.eq("service-1"),
                        org.mockito.ArgumentMatchers.isNull(),
                        requestCaptor.capture());
        assertEquals(Map.of("recordId", "project-1"),
                requestCaptor.getValue().getInput());
    }

    @Test
    void realDataTestRejectsWriteInterfaceBeforeExecution() {
        Fixture fixture = fixture();
        Map<String, Object> config = interfaceSpecialConfig(
                "INTERFACE_SERVICE", true, false);
        stubInterface(fixture, "WRITE");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.test(
                        "FORM", "form-1", config, "project-1"));

        assertTrue(failure.getMessage().contains("只允许选择 READ"));
        verify(fixture.dataSourceService, never())
                .previewRelatedContentReadOperation(
                        anyString(), anyString(), any());
    }

    @Test
    void realDataTestRejectsUnconditionedInterfaceOutput() {
        Fixture fixture = fixture();
        Map<String, Object> config = interfaceSpecialConfig(
                "INTERFACE_SERVICE", false, false);
        EntityDataDTO source = record(
                "project-1", "project", Map.of("ownerId", "user-9"));
        when(fixture.entityDataService.findAccessibleById(
                "project", "project-1", null)).thenReturn(source);
        stubInterface(fixture, "READ");
        when(fixture.dataSourceService.previewRelatedContentReadOperation(
                anyString(), any(), any()))
                .thenReturn(Map.of(
                        "total", 100,
                        "records", List.of(Map.of("id", "hidden-1"))));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.test(
                        "FORM", "form-1", config, "project-1"));

        assertEquals("UI_VIEW_COMPOSITION_INTERFACE_OUTPUT_INVALID",
                failure.getErrorCode());
        verify(fixture.entityDataService, never()).findPage(
                org.mockito.ArgumentMatchers.eq("requirement"),
                anyString(),
                any(),
                anyLong(),
                anyLong());
    }

    @Test
    void realDataTestPropagatesInterfaceExecutionFailure() {
        Fixture fixture = fixture();
        Map<String, Object> config = interfaceSpecialConfig(
                "INTERFACE_SERVICE", true, false);
        EntityDataDTO source = record(
                "project-1", "project", Map.of("ownerId", "user-9"));
        when(fixture.entityDataService.findAccessibleById(
                "project", "project-1", null)).thenReturn(source);
        stubInterface(fixture, "READ");
        when(fixture.dataSourceService.previewRelatedContentReadOperation(
                anyString(), any(), any()))
                .thenThrow(new BusinessConflictException(
                        "INTERFACE_TIMEOUT", "接口执行超时"));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.test(
                        "FORM", "form-1", config, "project-1"));

        assertEquals("INTERFACE_TIMEOUT", failure.getErrorCode());
    }

    @Test
    void realDataTestDoesNotBypassTargetDataPermission() {
        Fixture fixture = fixture();
        Map<String, Object> config = interfaceSpecialConfig(
                "INTERFACE_SERVICE", true, false);
        EntityDataDTO source = record(
                "project-1", "project", Map.of("ownerId", "user-9"));
        when(fixture.entityDataService.findAccessibleById(
                "project", "project-1", null)).thenReturn(source);
        stubInterface(fixture, "READ");
        when(fixture.dataSourceService.previewRelatedContentReadOperation(
                anyString(), any(), any()))
                .thenReturn(Map.of("resolvedProjectId", "project-1"));
        when(fixture.entityDataService.findPage(
                anyString(), anyString(), any(), anyLong(), anyLong()))
                .thenThrow(new ForbiddenException("目标数据无权访问"));

        assertThrows(
                ForbiddenException.class,
                () -> fixture.service.test(
                        "FORM", "form-1", config, "project-1"));
    }

    @Test
    void copyForOwnerRebuildsIdentityAndRemapsFormNodeAnchor() {
        Fixture fixture = fixture();
        UiViewComposition source = existing(fixture.codec, 7);
        source.setAnchorType("FORM_NODE");
        source.setAnchorKey("node-old");
        when(fixture.mapper.findByOwner("FORM", "form-1"))
                .thenReturn(List.of(source));

        fixture.service.copyForOwner(
                "FORM", "form-1", "form-copy",
                Map.of("node-old", "node-new"));

        ArgumentCaptor<UiViewComposition> captor =
                ArgumentCaptor.forClass(UiViewComposition.class);
        verify(fixture.mapper).insert(captor.capture());
        UiViewComposition copied = captor.getValue();
        assertEquals(null, copied.getId());
        assertEquals("form-copy", copied.getOwnerId());
        assertEquals("node-new", copied.getAnchorKey());
        assertEquals(1, copied.getRevision());
    }

    @Test
    void portableImportGeneratesNewIdentityAndTouchesOwnerOnce() {
        Fixture fixture = fixture();
        when(fixture.formMapper.update(isNull(), any())).thenReturn(1);

        fixture.service.importPortableDraft(
                "FORM",
                "form-1",
                List.of(Map.of(
                        "compositionKey", "project_requirements",
                        "anchorType", "OWNER",
                        "orderKey", 1000L,
                        "config", config())));

        ArgumentCaptor<UiViewComposition> captor =
                ArgumentCaptor.forClass(UiViewComposition.class);
        verify(fixture.mapper).deleteByOwner("FORM", "form-1");
        verify(fixture.mapper).insert(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals("form-1", captor.getValue().getOwnerId());
        assertEquals(1, captor.getValue().getRevision());
        verify(fixture.formMapper).update(isNull(), any());
    }

    @Test
    void ordinaryIdRelationSurvivesCompositionSnapshotAndPublicationValidation() {
        Fixture fixture = fixture();
        stubEntityRelation(fixture, EntityRelation.RelationType.ONE_TO_MANY);
        EntityField field = fixture.targetSnapshot.getFields().stream()
                .filter(item -> "projectId".equals(item.getFieldCode())).findFirst().orElseThrow();
        field.setFieldType(EntityField.FieldType.STRING);
        field.setRefEntityId(null);
        UiViewComposition current = existing(fixture.codec, 1);
        current.setAnchorType("OWNER");
        current.setConfigDocument(fixture.codec.write(relationConfig(), "test"));
        when(fixture.mapper.findByOwner("FORM", "form-1")).thenReturn(List.of(current));
        List<Map<String, Object>> snapshot = fixture.service.snapshot("FORM", "form-1");
        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", ownerSnapshot(snapshot, List.of())));
        field.setFieldType(EntityField.FieldType.LONG);
        assertThrows(BusinessConflictException.class, () -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", ownerSnapshot(snapshot, List.of())));
    }

    @Test
    void oneToManyRelationDisplaysListAndPinsRelationshipForPublication() {
        Fixture fixture = fixture();
        stubEntityRelation(fixture, EntityRelation.RelationType.ONE_TO_MANY);
        Map<String, Object> config = relationConfig();
        assertDoesNotThrow(() -> fixture.service.validate("FORM", "form-1", config));

        UiViewComposition current = existing(fixture.codec, 1);
        current.setAnchorType("OWNER");
        current.setConfigDocument(fixture.codec.write(config, "test"));
        when(fixture.mapper.findByOwner("FORM", "form-1")).thenReturn(List.of(current));
        List<Map<String, Object>> snapshot = fixture.service.snapshot("FORM", "form-1");
        assertDoesNotThrow(() -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", ownerSnapshot(snapshot, List.of())));
    }

    @Test
    void oneToOneRelationRejectsListAtSaveAndPublication() {
        Fixture fixture = fixture();
        stubEntityRelation(fixture, EntityRelation.RelationType.ONE_TO_ONE);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.validate("FORM", "form-1", relationConfig()));
        assertTrue(error.getMessage().contains("一对一"));

        UiViewComposition current = existing(fixture.codec, 1);
        current.setAnchorType("OWNER");
        current.setConfigDocument(fixture.codec.write(relationConfig(), "test"));
        when(fixture.mapper.findByOwner("FORM", "form-1")).thenReturn(List.of(current));
        List<Map<String, Object>> snapshot = fixture.service.snapshot("FORM", "form-1");
        assertThrows(IllegalArgumentException.class, () -> fixture.service.validateReleaseSnapshot(
                "FORM", "form-1", ownerSnapshot(snapshot, List.of())));
    }

    @Test
    void oneToOneRelationAcceptsFormButOneToManyCannotUseThatForm() {
        Fixture fixture = fixture();
        EntityForm form = new EntityForm();
        form.setId("form-target");
        form.setEntityId("requirement-entity");
        form.setActiveReleaseId("release-target");
        when(fixture.formMapper.selectById("form-target")).thenReturn(form);
        fixture.targetRelease.setConfigType("FORM");
        fixture.targetRelease.setConfigId("form-target");
        Map<String, Object> config = relationConfig();
        config.put("target", Map.of("entityId", "requirement-entity",
                "contentType", "FORM", "contentId", "form-target"));
        stubEntityRelation(fixture, EntityRelation.RelationType.ONE_TO_ONE);
        assertDoesNotThrow(() -> fixture.service.validate("FORM", "form-1", config));
        stubEntityRelation(fixture, EntityRelation.RelationType.ONE_TO_MANY);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.validate("FORM", "form-1", config));
        assertTrue(error.getMessage().contains("一对多"));
    }

    @Test
    void relationDisplayRejectsWrongEntityAndDisabledRelation() {
        Fixture fixture = fixture();
        EntityRelation relation = stubEntityRelation(fixture, EntityRelation.RelationType.ONE_TO_MANY);
        relation.setChildEntityId("another-entity");
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.validate("FORM", "form-1", relationConfig()));
        relation.setChildEntityId("requirement-entity");
        relation.setEnabled(false);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.validate("FORM", "form-1", relationConfig()));
    }

    private Map<String, Object> relationConfig() {
        Map<String, Object> value = new LinkedHashMap<>(config());
        value.put("relation", Map.of("type", "ENTITY_RELATION", "relationCode", "reqRelation"));
        value.put("presentation", Map.of("position", "INLINE", "loadMode", "IMMEDIATE"));
        return value;
    }

    private EntityRelation stubEntityRelation(Fixture fixture, EntityRelation.RelationType type) {
        EntityRelation relation = new EntityRelation();
        relation.setRelationCode("reqRelation");
        relation.setParentEntityId("project-entity");
        relation.setChildEntityId("requirement-entity");
        relation.setChildRefFieldCode("projectId");
        relation.setRelationType(type);
        relation.setEnabled(true);
        relation.setDeleted(0);
        when(fixture.relationMapper.selectByRelationCode("project-entity", "reqRelation"))
                .thenReturn(relation);
        fixture.sourceSnapshot.setRelations(List.of(relation));
        return relation;
    }

    private Fixture fixture() {
        UiViewCompositionMapper mapper = mock(UiViewCompositionMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        EntityRelationMapper relationMapper = mock(EntityRelationMapper.class);
        EntityDataDynamicService entityDataService =
                mock(EntityDataDynamicService.class);
        EntityPublishedSnapshotService entitySnapshotService =
                mock(EntityPublishedSnapshotService.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        UiExtensionDefinitionMapper dataSourceMapper =
                mock(UiExtensionDefinitionMapper.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        UiInterfaceExtensionService dataSourceService = mock(UiInterfaceExtensionService.class);
        UiExtensionDefinitionService extensionService =
                mock(UiExtensionDefinitionService.class);
        UiViewCompositionContainmentGuard containmentGuard =
                mock(UiViewCompositionContainmentGuard.class);
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());

        EntityForm owner = new EntityForm();
        owner.setId("form-1");
        owner.setEntityId("project-entity");
        owner.setRevision(1);
        when(formMapper.selectByIdForUpdate("form-1")).thenReturn(owner);
        when(formMapper.selectById("form-1")).thenReturn(owner);
        EntityDefinition sourceDefinition = new EntityDefinition();
        sourceDefinition.setId("project-entity");
        sourceDefinition.setEntityCode("project");
        EntityDefinition targetDefinition = new EntityDefinition();
        targetDefinition.setId("requirement-entity");
        targetDefinition.setEntityCode("requirement");
        when(definitionMapper.selectById("project-entity"))
                .thenReturn(sourceDefinition);
        when(definitionMapper.selectById("requirement-entity"))
                .thenReturn(targetDefinition);
        EntityListConfig target = new EntityListConfig();
        target.setId("list-target");
        target.setEntityId("requirement-entity");
        target.setActiveReleaseId("release-target");
        when(listMapper.selectById("list-target")).thenReturn(target);
        UiConfigRelease release = new UiConfigRelease();
        release.setId("release-target");
        release.setConfigType("LIST");
        release.setConfigId("list-target");
        release.setVersion(7);
        String targetDocument = codec.canonicalize(
                codec.write(targetListReleaseDocument(true),
                        "target release"),
                "target release");
        release.setSnapshotDocument(targetDocument);
        release.setContentHash(sha256(targetDocument));
        when(releaseMapper.selectById("release-target")).thenReturn(release);
        when(mapper.findByOwner("FORM", "form-1")).thenReturn(List.of());

        EntityPublishedSnapshot sourceSnapshot = new EntityPublishedSnapshot();
        sourceSnapshot.setHistoryId("project-history");
        sourceSnapshot.setEntityId("project-entity");
        sourceSnapshot.setEntityCode("project");
        sourceSnapshot.setEntityName("项目");
        sourceSnapshot.setVersion(3);
        EntityField selectedRequirementName = field(
                "selectedRequirementName", true, true);
        EntityField projectCode = field("projectCode", true, true);
        sourceSnapshot.setFields(List.of(
                selectedRequirementName,
                projectCode));
        sourceSnapshot.setRelations(List.of());
        sourceSnapshot.setRelationsSnapshotAvailable(true);
        EntityPublishedSnapshot targetSnapshot = new EntityPublishedSnapshot();
        targetSnapshot.setHistoryId("requirement-history");
        targetSnapshot.setEntityId("requirement-entity");
        targetSnapshot.setEntityCode("requirement");
        targetSnapshot.setEntityName("需求");
        targetSnapshot.setVersion(5);
        EntityField projectReference = new EntityField();
        projectReference.setFieldCode("projectId");
        projectReference.setFieldType(EntityField.FieldType.REFERENCE);
        projectReference.setRefEntityId("project-entity");
        EntityField requirementName = field(
                "requirementName", true, true);
        EntityField requirementProjectCode = field(
                "requirementProjectCode", true, true);
        targetSnapshot.setFields(List.of(
                projectReference,
                requirementName,
                requirementProjectCode));
        targetSnapshot.setRelations(List.of());
        targetSnapshot.setRelationsSnapshotAvailable(true);
        EntityPublishedSnapshotService.PinnedEntitySnapshot sourcePinned =
                new EntityPublishedSnapshotService.PinnedEntitySnapshot(
                        sourceSnapshot, "a".repeat(64));
        EntityPublishedSnapshotService.PinnedEntitySnapshot targetPinned =
                new EntityPublishedSnapshotService.PinnedEntitySnapshot(
                        targetSnapshot, "b".repeat(64));
        when(entitySnapshotService.getLatestPinnedByEntityId(
                "project-entity")).thenReturn(sourcePinned);
        when(entitySnapshotService.getLatestPinnedByEntityId(
                "requirement-entity")).thenReturn(targetPinned);
        when(entitySnapshotService.getPinnedByHistoryId(
                "project-history")).thenReturn(sourcePinned);
        when(entitySnapshotService.getPinnedByHistoryId(
                "requirement-history")).thenReturn(targetPinned);

        UiViewCompositionService service = new UiViewCompositionService(
                mapper,
                formMapper,
                listMapper,
                definitionMapper,
                relationMapper,
                entityDataService,
                entitySnapshotService,
                releaseMapper,
                dataSourceMapper,
                accessService,
                dataSourceService,
                extensionService,
                new UiViewCompositionConfigValidator(),
                containmentGuard,
                codec);
        return new Fixture(
                service,
                mapper,
                formMapper,
                entityDataService,
                dataSourceService,
                extensionService,
                containmentGuard,
                codec,
                release,
                relationMapper,
                sourceSnapshot,
                targetSnapshot);
    }

    private UiViewCompositionSaveRequest request(Integer revision) {
        UiViewCompositionSaveRequest request =
                new UiViewCompositionSaveRequest();
        request.setExpectedRevision(revision);
        if (revision != null) {
            request.setExpectedOwnerRevision(1);
        }
        request.setCompositionKey("project_requirements");
        request.setAnchorType("FORM_END");
        request.setOrderKey(1000L);
        request.setConfig(config());
        return request;
    }

    private Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("schemaVersion", 1);
        config.put("name", "项目需求");
        config.put("enabled", true);
        config.put("source", Map.of(
                "entityId", "project-entity",
                "entityCode", "project",
                "entityName", "项目"));
        config.put("target", Map.of(
                "entityId", "requirement-entity",
                "entityCode", "requirement",
                "entityName", "需求",
                "contentType", "LIST",
                "contentId", "list-target",
                "contentKey", "project_requirements",
                "contentName", "需求列表"));
        config.put("presentation", Map.of(
                "position", "INLINE", "loadMode", "ON_DEMAND"));
        config.put("relation", Map.of(
                "type", "REVERSE_REFERENCE",
                "targetField", "projectId",
                "targetFieldName", "所属项目",
                "mappings", List.of()));
        config.put("actions", List.of("VIEW"));
        config.put("specialHandling", Map.of(
                "mode", "NONE",
                "failurePolicy", "ERROR"));
        return config;
    }

    private Map<String, Object> interfaceConfig() {
        Map<String, Object> config = new LinkedHashMap<>(config());
        config.put("relation", Map.of("type", "INTERFACE_SERVICE"));
        config.put("specialHandling", Map.of(
                "mode", "INTERFACE_SERVICE",
                "failurePolicy", "ERROR",
                "interfaceService", Map.of(
                        "extensionId", "service-1",
                        "inputMappings", List.of(),
                        "outputMappings", List.of())));
        return config;
    }

    private Map<String, Object> legacyInterfaceConfig() {
        Map<String, Object> config = new LinkedHashMap<>(config());
        config.put("relation", Map.of("type", "INTERFACE_SERVICE"));
        config.put("specialHandling", Map.of(
                "mode", "INTERFACE_SERVICE",
                "failurePolicy", "ERROR",
                "interfaceService", Map.of(
                        "serviceId", "legacy-service",
                        "operationCode", "resolveRequirements",
                        "serviceRevision", 2,
                        "sourceCode", "projectRequirements",
                        "inputMappings", List.of(),
                        "outputMappings", List.of())));
        return config;
    }

    private UiExtensionDefinition interfaceDefinition(
            String id,
            String status) {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId(id);
        definition.setExtensionType("INTERFACE");
        definition.setInterfaceKind("READ");
        definition.setInterfaceContextType("FORM");
        definition.setStatus(status);
        definition.setDeleted(0);
        return definition;
    }

    private Map<String, Object> interfaceSpecialConfig(
            String mode,
            boolean includeMappings,
            boolean includeComponent) {
        Map<String, Object> config = new LinkedHashMap<>(config());
        List<Map<String, Object>> inputMappings = includeMappings
                ? List.of(Map.of(
                "sourceField", "ownerId",
                "target", "criteria.ownerId",
                "required", true))
                : List.of();
        List<Map<String, Object>> outputMappings =
                includeMappings
                        ? List.of(Map.of(
                        "source", "resolvedProjectId",
                        "target", "fixedFilters.projectId",
                        "required", true))
                        : List.of();
        Map<String, Object> special = new LinkedHashMap<>();
        special.put("mode", mode);
        special.put("failurePolicy", "ERROR");
        special.put("interfaceService", Map.of(
                "extensionId", "service-1",
                "inputMappings", inputMappings,
                "outputMappings", outputMappings));
        if (includeComponent) {
            special.put("customComponent", Map.of(
                    "name", "project-timeline",
                    "version", 2,
                    "artifactDigest", "b".repeat(64),
                    "props", Map.of()));
        }
        config.put("specialHandling", special);
        return config;
    }

    private void stubInterface(Fixture fixture, String kind) {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("service-1");
        definition.setExtensionType("INTERFACE");
        definition.setInterfaceKind(kind);
        definition.setInterfaceContextType("FORM");
        definition.setEnabled(true);
        definition.setDeleted(0);
        when(fixture.dataSourceService.requireExecutableDefinition(
                "service-1", null)).thenReturn(definition);
    }

    private EntityDataDTO record(
            String id,
            String entityCode,
            Map<String, Object> data) {
        EntityDataDTO result = new EntityDataDTO();
        result.setId(id);
        result.setEntityCode(entityCode);
        result.setTitle(id);
        result.setData(data);
        return result;
    }

    private Map<String, Object> componentConfig() {
        Map<String, Object> config = new LinkedHashMap<>(config());
        config.put("specialHandling", Map.of(
                "mode", "CUSTOM_COMPONENT",
                "failurePolicy", "ERROR",
                "customComponent", Map.of(
                        "name", "project-timeline",
                        "version", 2,
                        "artifactDigest", "b".repeat(64),
                        "props", Map.of())));
        return config;
    }

    private Map<String, Object> ownerSnapshot(
            List<Map<String, Object>> compositions,
            List<Map<String, Object>> nodes) {
        return ownerSnapshot(compositions, nodes, null);
    }

    private Map<String, Object> ownerSnapshot(
            List<Map<String, Object>> compositions,
            List<Map<String, Object>> nodes,
            String customComponent) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("configType", "FORM");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("id", "form-1");
        form.put("entityId", "project-entity");
        if (customComponent != null) {
            form.put("customComponent", customComponent);
        }
        snapshot.put("form", form);
        snapshot.put("nodes", nodes);
        snapshot.put("viewCompositions", compositions);
        return snapshot;
    }

    private static EntityField field(
            String fieldCode,
            boolean readable,
            boolean editable) {
        EntityField field = new EntityField();
        field.setFieldCode(fieldCode);
        field.setFieldType(EntityField.FieldType.STRING);
        field.setRuntimeReadable(readable);
        field.setEditable(editable);
        return field;
    }

    private static Map<String, Object> targetListReleaseDocument(
            boolean showField) {
        return Map.of(
                "schemaVersion", 1,
                "configType", "LIST",
                "list", Map.of(
                        "id", "list-target",
                        "entityId", "requirement-entity",
                        "fields", List.of(Map.of(
                                "fieldCode", "requirementName",
                                "showInList", showField,
                                "dataSourceType", "ENTITY_FIELD"))));
    }

    private static Map<String, Object> targetFormReleaseDocument(
            boolean readonly) {
        return Map.of(
                "schemaVersion", 1,
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-target",
                        "entityId", "requirement-entity"),
                "legacyFields", List.of(Map.of(
                        "fieldCode", "requirementProjectCode",
                        "isHidden", 0,
                        "isReadonly", readonly ? 1 : 0)));
    }

    private void updateTargetRelease(
            Fixture fixture,
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        String document = fixture.codec.canonicalize(
                fixture.codec.write(snapshot, "target release"),
                "target release");
        fixture.targetRelease.setConfigType(configType);
        fixture.targetRelease.setConfigId(configId);
        fixture.targetRelease.setSnapshotDocument(document);
        fixture.targetRelease.setContentHash(sha256(document));
    }

    private Map<String, Object> listOwnerSnapshot(
            List<Map<String, Object>> compositions,
            String customComponent) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("configType", "LIST");
        snapshot.put("list", Map.of(
                "id", "list-1",
                "entityId", "project-entity",
                "customComponent", customComponent));
        List<Map<String, Object>> copied = fixtureCompositionCopy(compositions);
        Map<String, Object> item = copied.get(0);
        item.put("anchorType", "PAGE_SECTION");
        item.put("anchorKey", "after-table");
        snapshot.put("viewCompositions", copied);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fixtureCompositionCopy(
            List<Map<String, Object>> compositions) {
        JsonDocumentCodec copyCodec = new JsonDocumentCodec(new ObjectMapper());
        Map<String, Object> wrapper = copyCodec.readObject(
                copyCodec.write(Map.of("items", compositions), "test"),
                "test");
        return (List<Map<String, Object>>) (List<?>) wrapper.get("items");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UiViewComposition existing(
            JsonDocumentCodec codec,
            int revision) {
        UiViewComposition value = new UiViewComposition();
        value.setId("composition-1");
        value.setOwnerType("FORM");
        value.setOwnerId("form-1");
        value.setCompositionKey("project_requirements");
        value.setAnchorType("FORM_END");
        value.setConfigDocument(codec.write(config(), "test"));
        value.setOrderKey(1000L);
        value.setRevision(revision);
        value.setDeleted(0);
        return value;
    }

    private record Fixture(
            UiViewCompositionService service,
            UiViewCompositionMapper mapper,
            EntityFormMapper formMapper,
            EntityDataDynamicService entityDataService,
            UiInterfaceExtensionService dataSourceService,
            UiExtensionDefinitionService extensionService,
            UiViewCompositionContainmentGuard containmentGuard,
            JsonDocumentCodec codec,
            UiConfigRelease targetRelease,
            EntityRelationMapper relationMapper,
            EntityPublishedSnapshot sourceSnapshot,
            EntityPublishedSnapshot targetSnapshot) {
    }
}
