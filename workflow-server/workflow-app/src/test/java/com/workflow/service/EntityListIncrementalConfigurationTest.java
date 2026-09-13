package com.workflow.service;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.list.application.EntityListRelationalConfigService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.permission.application.EntityListActionRulePolicy;

import com.workflow.entity.list.api.request.EntityListActionSaveRequest;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListAction;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListSceneMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体列表增量配置测试。
 *
 * <p>被测对象：{@link EntityListConfigService} 与 {@link EntityListRelationalConfigService}，
 * 覆盖字段补丁显式清空可选绑定、动作创建持久化显式排序等增量配置场景。
 */
class EntityListIncrementalConfigurationTest {

    @Test
    void listFieldBooleanFlagsAlwaysParticipateInInsertAndUpdate() throws Exception {
        for (String fieldName : List.of("showInList", "isQuery")) {
            Field field = EntityListField.class.getDeclaredField(fieldName);
            TableField mapping = field.getAnnotation(TableField.class);

            assertEquals(FieldStrategy.ALWAYS, mapping.insertStrategy());
            assertEquals(FieldStrategy.ALWAYS, mapping.updateStrategy());
        }
    }

    @Test
    void systemListRejectsUnsupportedQueryOperator() {
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        SystemEntityFieldPolicy fieldPolicy =
                mock(SystemEntityFieldPolicy.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setId("system-user");
        entity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        EntityField entityField = new EntityField();
        entityField.setId("username-field");
        entityField.setFieldCode("username");
        when(definitionMapper.selectById("system-user"))
                .thenReturn(entity);
        when(fieldMapper.findByEntityId("system-user"))
                .thenReturn(List.of(entityField));
        when(fieldPolicy.isUiConfigurable(entity, entityField))
                .thenReturn(true);

        EntityListConfigService service =
                new EntityListConfigService(
                        null,
                        null,
                        definitionMapper,
                        fieldMapper,
                        fieldPolicy,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        EntityListConfigDTO config = new EntityListConfigDTO();
        config.setEntityId("system-user");
        EntityListField configured = new EntityListField();
        configured.setFieldId("username-field");
        configured.setFieldCode("username");
        configured.setIsQuery(true);
        configured.setQueryType("NOT_IN");
        config.setFields(List.of(configured));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "validateSystemListConfiguration",
                        config));

        assertTrue(error.getMessage().contains("不支持查询方式"));
    }

    /**
     * 测试字段补丁可显式清空可选绑定：
     * 验证字段属性支持类将源字段为空的属性复制到目标后，相关绑定被清空为 null。
     */
    @Test
    void fieldPatchCanExplicitlyClearOptionalBindings() {
        EntityListConfigService service = new EntityListConfigService(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null);
        EntityListField source = new EntityListField();
        EntityListField target = new EntityListField();
        target.setDataSourceId("source-1");
        target.setTemplateId("template-1");
        target.setTemplateVersion(3);
        target.setLocalOverridesDocument("{}");

        Object fieldProperties = ReflectionTestUtils.getField(
                service,
                "fieldProperties");
        ReflectionTestUtils.invokeMethod(
                fieldProperties,
                "copyMutable",
                source,
                target,
                Set.of(
                        "dataSourceId",
                        "templateId",
                        "templateVersion",
                        "localOverridesDocument"));

        assertNull(target.getDataSourceId());
        assertNull(target.getTemplateId());
        assertNull(target.getTemplateVersion());
        assertNull(target.getLocalOverridesDocument());
    }

    /**
     * 测试动作创建持久化显式排序值：
     * 验证保存动作时 sortOrder 与 orderKey 按请求显式值落库，不被自动覆盖。
     */
    @Test
    void actionCreatePersistsExplicitSortOrder() {
        EntityListActionMapper actionMapper = mock(EntityListActionMapper.class);
        EntityListSceneMapper sceneMapper = mock(EntityListSceneMapper.class);
        EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        when(configMapper.selectById("list-1")).thenReturn(config);
        when(actionMapper.findByListAndPosition("list-1", "TOOLBAR"))
                .thenReturn(List.of());
        when(actionMapper.insert(any(EntityListAction.class))).thenReturn(1);

        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        actionMapper,
                        sceneMapper,
                        configMapper,
                        mock(com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper.class),
                        mock(com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper.class),
                        null,
                        listActionRulePolicy());
        EntityListActionSaveRequest request = new EntityListActionSaveRequest();
        request.setPosition("TOOLBAR");
        request.setButtonKey("custom_review");
        request.setButtonLabel("复核");
        request.setSortOrder(9);
        request.setOrderKey(10_000_000L);

        EntityListAction saved = service.createAction("list-1", request);

        assertEquals(9, saved.getSortOrder());
        assertEquals(10_000_000L, saved.getOrderKey());
    }

    @Test
    void actionCreateRejectsNonV2OrMalformedAvailabilityRule() {
        EntityListActionMapper actionMapper =
                mock(EntityListActionMapper.class);
        EntityListConfigMapper configMapper =
                mock(EntityListConfigMapper.class);
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        when(configMapper.selectById("list-1")).thenReturn(config);
        when(actionMapper.findByListAndPosition("list-1", "ROW"))
                .thenReturn(List.of());
        when(actionMapper.insert(any(EntityListAction.class)))
                .thenReturn(1);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        actionMapper,
                        mock(EntityListSceneMapper.class),
                        configMapper,
                        mock(EntityFormMapper.class),
                        mock(UiConfigReleaseMapper.class),
                        codec,
                        listActionRulePolicy());
        EntityListActionSaveRequest request =
                new EntityListActionSaveRequest();
        request.setPosition("ROW");
        request.setButtonKey("review");
        request.setButtonLabel("复核");

        request.setAvailabilityRule(Map.of("version", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAction("list-1", request));

        request.setAvailabilityRule(Map.of(
                "version", 2,
                "visibleWhen", Map.of(
                        "type", "FIELD",
                        "operator", "EQ",
                        "value", "x"),
                "disabledMessage", ""));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAction("list-1", request));

        request.setAvailabilityRule(Map.of(
                "version", 2,
                "visibleWhen", Map.of(
                        "type", "GROUP",
                        "logic", "AND",
                        "children", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAction("list-1", request));

        request.setAvailabilityRule(Map.of(
                "version", 2,
                "visibleWhen", Map.of(
                        "type", "STATUS_CODE",
                        "operator", "IN",
                        "value", "DRAFT, REVIEW"),
                "disabledMessage", ""));
        EntityListAction saved = service.createAction("list-1", request);
        Map<String, Object> normalized = codec.readObject(
                saved.getAvailabilityRuleDocument(), "test");
        @SuppressWarnings("unchecked")
        Map<String, Object> visibleWhen =
                (Map<String, Object>) normalized.get("visibleWhen");
        assertEquals(List.of("DRAFT", "REVIEW"),
                visibleWhen.get("value"));
    }

    @Test
    void actionPatchPayloadCanExplicitlyClearAvailabilityRule() {
        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        mock(EntityListActionMapper.class),
                        mock(EntityListSceneMapper.class),
                        mock(EntityListConfigMapper.class),
                        mock(EntityFormMapper.class),
                        mock(UiConfigReleaseMapper.class),
                        new JsonDocumentCodec(new ObjectMapper()),
                        listActionRulePolicy());
        EntityListAction action = new EntityListAction();
        action.setButtonKey("edit");
        action.setButtonLabel("编辑");
        action.setAvailabilityRuleDocument(
                "{\"version\":2,\"visibleWhen\":null,"
                        + "\"enabledWhen\":null,"
                        + "\"disabledMessage\":\"\"}");
        action.setActionParamsDocument(
                "{\"availabilityRule\":{\"version\":2},"
                        + "\"custom\":\"kept\"}");
        EntityListActionSaveRequest patch =
                new EntityListActionSaveRequest();
        patch.setClearFields(Set.of("availabilityRuleDocument"));

        ReflectionTestUtils.invokeMethod(
                service, "applyAction", action, patch);

        assertNull(action.getAvailabilityRuleDocument());
        @SuppressWarnings("unchecked")
        Map<String, Object> button = ReflectionTestUtils.invokeMethod(
                service, "toButton", action);
        assertTrue(!button.containsKey("availabilityRule"));
        assertEquals("kept", button.get("custom"));
    }

    @Test
    void actionPatchRejectsInvalidAvailabilityRuleBeforeWrite() {
        EntityListActionMapper actionMapper =
                mock(EntityListActionMapper.class);
        EntityListAction current = new EntityListAction();
        current.setId("action-1");
        current.setListConfigId("list-1");
        current.setRevision(1);
        current.setButtonKey("edit");
        current.setButtonLabel("编辑");
        when(actionMapper.selectById("action-1"))
                .thenReturn(current);
        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        actionMapper,
                        mock(EntityListSceneMapper.class),
                        mock(EntityListConfigMapper.class),
                        mock(EntityFormMapper.class),
                        mock(UiConfigReleaseMapper.class),
                        new JsonDocumentCodec(new ObjectMapper()),
                        listActionRulePolicy());
        EntityListActionSaveRequest patch =
                new EntityListActionSaveRequest();
        patch.setExpectedRevision(1);
        patch.setAvailabilityRule(Map.of("version", 1));

        assertThrows(IllegalArgumentException.class,
                () -> service.patchAction(
                        "list-1", "action-1", patch));
        verify(actionMapper, never()).update(any(), any());
    }

    @Test
    void actionReplaceRejectsInvalidAvailabilityRuleBeforeWrite() {
        EntityListActionMapper actionMapper =
                mock(EntityListActionMapper.class);
        EntityListConfigMapper configMapper =
                mock(EntityListConfigMapper.class);
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        when(configMapper.selectById("list-1")).thenReturn(config);
        when(actionMapper.findByListAndPosition("list-1", "ROW"))
                .thenReturn(List.of());
        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        actionMapper,
                        mock(EntityListSceneMapper.class),
                        configMapper,
                        mock(EntityFormMapper.class),
                        mock(UiConfigReleaseMapper.class),
                        new JsonDocumentCodec(new ObjectMapper()),
                        listActionRulePolicy());

        assertThrows(IllegalArgumentException.class,
                () -> service.replaceActions(
                        "list-1", "ROW", List.of(Map.of(
                                "key", "edit",
                                "label", "编辑",
                                "availabilityRule", Map.of(
                                        "version", 1)))));
        verify(actionMapper, never()).insert(
                any(EntityListAction.class));
    }

    @Test
    void actionTargetFormMustBelongToListEntityAndBePublished() {
        EntityListActionMapper actionMapper =
                mock(EntityListActionMapper.class);
        EntityListConfigMapper configMapper =
                mock(EntityListConfigMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        UiConfigReleaseMapper releaseMapper =
                mock(UiConfigReleaseMapper.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setEntityId("entity-1");
        when(configMapper.selectById("list-1"))
                .thenReturn(config);
        when(actionMapper.findByListAndPosition(
                "list-1",
                "ROW"))
                .thenReturn(List.of());

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setStatus(1);
        form.setActiveReleaseId("release-1");
        when(formMapper.selectById("form-1")).thenReturn(form);
        UiConfigRelease release = new UiConfigRelease();
        release.setId("release-1");
        release.setConfigType("FORM");
        release.setConfigId("form-1");
        release.setVersion(3);
        when(releaseMapper.findActive("FORM", "form-1"))
                .thenReturn(release);

        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        actionMapper,
                        mock(EntityListSceneMapper.class),
                        configMapper,
                        formMapper,
                        releaseMapper,
                        codec,
                        listActionRulePolicy());
        EntityListActionSaveRequest request =
                new EntityListActionSaveRequest();
        request.setPosition("ROW");
        request.setButtonKey("view");
        request.setButtonType("built-in");
        request.setButtonLabel("查看");
        request.setActionParams(Map.of(
                "targetFormId",
                "form-1",
                "targetFormReleaseId",
                "client-release"));

        EntityListAction saved =
                service.createAction("list-1", request);
        Map<String, Object> savedParams = codec.readObject(
                saved.getActionParamsDocument(),
                "test");
        assertEquals("form-1", savedParams.get("targetFormId"));
        assertTrue(!savedParams.containsKey("targetFormReleaseId"));

        form.setEntityId("entity-2");
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createAction("list-1", request));
        assertTrue(error.getMessage().contains("当前列表实体"));
    }

    @Test
    void legacyReleaseActionUsesCurrentIdButPublishedPersistenceDefaults() {
        Map<String, Object> legacy = Map.of(
                "key", "create",
                "buttonType", "primary",
                "perm", "entity:demo_entity:create");
        Map<String, Object> current = Map.of(
                "key", "create",
                "id", "stable-current-action",
                "link", true,
                "sort", 9,
                "orderKey", 9_000_000L);

        List<Map<String, Object>> normalized =
                EntityListRelationalConfigService
                        .normalizeReleaseActionPersistenceDefaults(
                                "list-1",
                                EntityListRelationalConfigService.TOOLBAR,
                                List.of(legacy),
                                List.of(current));
        Map<String, Object> restored = normalized.get(0);
        assertEquals("stable-current-action", restored.get("id"));
        assertEquals(1_000_000L, restored.get("orderKey"));
        assertEquals(0, restored.get("sort"));
        assertEquals("built-in", restored.get("type"));
        assertEquals("create", restored.get("label"));
        assertEquals(false, restored.get("link"));
        assertEquals(true, restored.get("enabled"));

        EntityListActionMapper actionMapper =
                mock(EntityListActionMapper.class);
        EntityListConfigMapper configMapper =
                mock(EntityListConfigMapper.class);
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setEntityId("entity-1");
        when(configMapper.selectById("list-1"))
                .thenReturn(config);
        java.util.concurrent.atomic.AtomicReference<EntityListAction>
                persisted = new java.util.concurrent.atomic.AtomicReference<>();
        when(actionMapper.findByListAndPosition(
                "list-1",
                EntityListRelationalConfigService.TOOLBAR))
                .thenAnswer(invocation -> persisted.get() == null
                        ? List.of()
                        : List.of(persisted.get()));
        when(actionMapper.insert(any(EntityListAction.class)))
                .thenAnswer(invocation -> {
                    persisted.set(invocation.getArgument(0));
                    return 1;
                });
        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        actionMapper,
                        mock(EntityListSceneMapper.class),
                        configMapper,
                        mock(EntityFormMapper.class),
                        mock(UiConfigReleaseMapper.class),
                        new JsonDocumentCodec(new ObjectMapper()),
                        listActionRulePolicy());

        service.replaceActionsForRelease(
                "list-1",
                EntityListRelationalConfigService.TOOLBAR,
                normalized);

        ArgumentCaptor<EntityListAction> actionCaptor =
                ArgumentCaptor.forClass(EntityListAction.class);
        verify(actionMapper).insert(actionCaptor.capture());
        assertEquals(
                "stable-current-action",
                actionCaptor.getValue().getId());
        assertEquals(
                1_000_000L,
                actionCaptor.getValue().getOrderKey());
        assertEquals(false, actionCaptor.getValue().getLinkMode());
        Map<String, Object> roundTrip = service.findActions(
                "list-1",
                EntityListRelationalConfigService.TOOLBAR).get(0);
        assertEquals("stable-current-action", roundTrip.get("id"));
        assertEquals(1_000_000L, roundTrip.get("orderKey"));
        assertEquals(0, roundTrip.get("sort"));
        assertEquals(false, roundTrip.get("link"));
    }

    @Test
    void legacyReleaseRejectsCaseInsensitiveDuplicateActionKeys() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> EntityListRelationalConfigService
                        .normalizeReleaseActionPersistenceDefaults(
                                "list-1",
                                EntityListRelationalConfigService.ROW,
                                List.of(
                                        Map.of("key", "Review"),
                                        Map.of("key", "review")),
                                List.of()));

        assertTrue(error.getMessage().contains("按钮编码重复"));
    }

    @Test
    void releaseRestorePreservesApproveAcrossLifecycleDrift()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityDefinition definition = new EntityDefinition();
        definition.setLifecycleMode(
                EntityDefinition.LifecycleMode.STANDALONE);
        when(definitionMapper.findByEntityCode("demo_entity"))
                .thenReturn(java.util.Optional.of(definition));
        EntityListRelationalConfigService relationalConfigService =
                mock(EntityListRelationalConfigService.class);
        EntityListActionConfigService service =
                new EntityListActionConfigService(
                        objectMapper,
                        definitionMapper,
                        mock(EntityListConfigMapper.class),
                        relationalConfigService,
                        listActionRulePolicy(),
                        List.of());
        EntityListConfigDTO published = new EntityListConfigDTO();
        published.setId("list-1");
        published.setEntityCode("demo_entity");
        published.setToolbarConfig(List.of());
        published.setRowActionConfig(List.of(Map.of(
                "key", "approve",
                "type", "built-in",
                "label", "审批")));

        service.normalizePublishedActionsForRestore(
                published,
                null);

        assertEquals(
                "approve",
                published.getRowActionConfig().get(0).get("key"));

        EntityListConfig persistent = new EntityListConfig();
        persistent.setId("list-1");
        persistent.setEntityCode("demo_entity");
        persistent.setToolbarConfig("[]");
        persistent.setRowActionConfig(objectMapper.writeValueAsString(
                List.of(Map.of(
                        "key", "approve",
                        "type", "built-in",
                        "label", "审批"))));
        service.normalizeForReleaseRestore(persistent);
        List<?> restoredRows = objectMapper.readValue(
                persistent.getRowActionConfig(),
                List.class);
        assertEquals(
                "approve",
                ((Map<?, ?>) restoredRows.get(0)).get("key"));

        service.synchronizeRelationalConfigForRelease(persistent);
        verify(relationalConfigService).replaceActionsForRelease(
                org.mockito.ArgumentMatchers.eq("list-1"),
                org.mockito.ArgumentMatchers.eq(
                        EntityListRelationalConfigService.ROW),
                org.mockito.ArgumentMatchers.argThat(buttons ->
                        buttons.size() == 1
                                && "approve".equals(
                                buttons.get(0).get("key"))));
    }

    private static EntityListActionRulePolicy listActionRulePolicy() {
        return new EntityListActionRulePolicy(
                new ObjectMapper(), List.of());
    }
}
