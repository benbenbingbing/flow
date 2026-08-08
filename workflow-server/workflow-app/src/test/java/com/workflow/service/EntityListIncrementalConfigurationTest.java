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
                        null);
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
                        codec);
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
}
