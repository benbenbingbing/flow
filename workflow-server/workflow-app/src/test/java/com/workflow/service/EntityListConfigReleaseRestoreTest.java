package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.list.application.EntityListRelationalConfigService;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.permission.application.EntityPermissionCatalogService;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;
import com.workflow.entity.ui.application.validation.UiConfigInterfaceReferenceValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 使用真实整包校验器回归撤销入口，防止新配置规则阻止恢复旧发布快照。 */
class EntityListConfigReleaseRestoreTest {

    /** 撤销保留旧来源、稳定 ID、JSON 及两种排序值，只重置草稿字段修订号。 */
    @Test
    void restoresLegacyPublishedFieldsWithoutRevalidatingOrNormalizingTheirContent() {
        Context context = new Context();
        EntityListConfigDTO published = publishedSnapshot();
        List<EntityListField> expected = published.getFields().stream().map(field -> {
            EntityListField copy = new EntityListField();
            BeanUtils.copyProperties(field, copy);
            return copy;
        }).toList();

        EntityListConfigDTO restored = context.service.restoreConfigForRelease(published, 5);

        assertEquals(6, restored.getRevision());
        assertEquals("release-1", restored.getActiveReleaseId());
        assertEquals(2, restored.getPublishedVersion());
        assertEquals("已发布列表", restored.getListName());
        assertEquals(published.getViewConfig(), restored.getViewConfig());
        assertEquals(published.getSelectionConfig(), restored.getSelectionConfig());
        assertThat(restored.getFields()).usingRecursiveComparison()
                .ignoringFields("revision", "createdAt", "updatedAt").isEqualTo(expected);
        assertEquals(List.of("published-legacy", "published-template"),
                restored.getFields().stream().map(EntityListField::getId).toList());
        assertEquals(List.of(9, 3), restored.getFields().stream()
                .map(EntityListField::getSortOrder).toList());
        assertEquals(List.of(1_000_000L, 2_000_000L), restored.getFields().stream()
                .map(EntityListField::getOrderKey).toList());
        assertNull(restored.getFields().get(0).getQueryType(),
                "撤销不能使用新校验规则补写旧快照中的查询默认值");
        assertThat(restored.getFields()).allMatch(field -> field.getRevision() == 1);
        verify(context.fieldMapper).deleteByListConfigId("list-1");
        verify(context.actions).deleteRelationalConfig("list-1");
        verify(context.actions, never()).normalizeForSave(any());
        verify(context.actions, never()).synchronizeRelationalConfig(any());
        ArgumentCaptor<EntityListConfig> config = ArgumentCaptor.forClass(EntityListConfig.class);
        verify(context.actions).synchronizeRelationalConfigForRelease(config.capture());
        assertEquals("PROJECT_CUSTOM_FIELD", restored.getFields().get(0).getDataSourceType());
        assertEquals("list-1", config.getValue().getId());
        verifyNoInteractions(context.interfaceReferences);
    }

    /** 普通保存和系统导入仍校验同一份旧配置，撤销的例外不能扩散到写入入口。 */
    @ParameterizedTest
    @ValueSource(strings = {"USER_CAS", "SYSTEM_IMPORT", "LEGACY_IMPORT"})
    void ordinarySaveAndImportStillRejectLegacyDataSources(String mode) {
        Context context = new Context();
        EntityListConfigDTO published = publishedSnapshot();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> {
            switch (mode) {
                case "USER_CAS" -> context.service.saveConfig(published, 5);
                case "SYSTEM_IMPORT" -> context.service.saveConfigForImport(published);
                default -> context.service.saveConfig(published);
            }
        });

        assertThat(error.getMessage()).contains("PROJECT_CUSTOM_FIELD", "项目扩展列", "取值配置与当前版本不兼容");
        context.verifyNoWrites();
    }

    /** 系统列表撤销也不能受当前配置规则阻塞；普通保存继续拒绝旧的自定义查询配置。 */
    @Test
    void releaseRestoreSkipsSystemListConfigurationRulesOnlyForHistoricalSnapshot() {
        Context context = new Context();
        EntityDefinition systemEntity = new EntityDefinition();
        systemEntity.setId("entity-1");
        systemEntity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        when(context.definitionMapper.selectById("entity-1")).thenReturn(systemEntity);
        EntityListConfigDTO published = publishedSnapshot();
        published.setCustomComponent("LegacyListComponent");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> context.service.saveConfig(published, 5));
        assertEquals("平台系统表列表只能使用可信只读查询", error.getMessage());
        context.verifyNoWrites();

        EntityListConfigDTO restored = context.service.restoreConfigForRelease(published, 5);

        assertEquals("LegacyListComponent", restored.getCustomComponent());
        assertEquals("PROJECT_CUSTOM_FIELD", restored.getFields().get(0).getDataSourceType());
    }

    /** revision 缺失或过期时必须在重建字段与按钮之前拒绝，避免覆盖其他人的草稿。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {4})
    void releaseRestoreRequiresCurrentRevisionBeforeWriting(Integer expectedRevision) {
        Context context = new Context();

        if (expectedRevision == null) {
            assertEquals("expectedRevision 不能为空", assertThrows(IllegalArgumentException.class,
                    () -> context.service.restoreConfigForRelease(publishedSnapshot(), null)).getMessage());
        } else {
            RevisionConflictException error = assertThrows(RevisionConflictException.class,
                    () -> context.service.restoreConfigForRelease(publishedSnapshot(), expectedRevision));
            assertEquals(5, ((EntityListConfigDTO) error.getCurrentData()).getRevision());
        }

        context.verifyNoWrites();
    }

    /** 绕过内容校验不等于绕过实体配置权限。 */
    @Test
    void releaseRestoreRetainsEntityAccessCheckBeforeWriting() {
        Context context = new Context();
        when(context.accessPolicy.requireConfigurableById("entity-1"))
                .thenThrow(new ForbiddenException("无权配置此实体"));

        assertEquals("无权配置此实体", assertThrows(ForbiddenException.class,
                () -> context.service.restoreConfigForRelease(publishedSnapshot(), 5)).getMessage());

        context.verifyNoWrites();
    }

    /** 恢复独立数据范围仍要求管理员，避免借历史快照提升数据访问权限。 */
    @Test
    void releaseRestoreRetainsOverridePermissionBeforeWriting() {
        Context context = new Context();
        EntityListConfigDTO published = publishedSnapshot();
        published.setDataScopeMode("OVERRIDE");

        assertEquals("只有超级管理员可以将列表配置为独立数据范围",
                assertThrows(ForbiddenException.class,
                        () -> context.service.restoreConfigForRelease(published, 5)).getMessage());

        context.verifyNoWrites();
    }

    /** owner 条件更新失败时不得删除子项，保留数据库最后一道并发保护。 */
    @Test
    void releaseRestoreStopsBeforeChildRebuildWhenOwnerCasFails() {
        Context context = new Context();
        when(context.configMapper.update(isNull(), any())).thenReturn(0);

        assertThrows(RevisionConflictException.class,
                () -> context.service.restoreConfigForRelease(publishedSnapshot(), 5));

        context.verifyNoChildWrites();
    }

    /** 构造含旧 provider 的发布快照，并刻意保留非数组顺序的 sortOrder 和原始 JSON 格式。 */
    private EntityListConfigDTO publishedSnapshot() {
        EntityListConfigDTO dto = new EntityListConfigDTO();
        dto.setId("list-1");
        dto.setEntityId("entity-1");
        dto.setEntityCode("project");
        dto.setListKey("default");
        dto.setListName("已发布列表");
        dto.setDataScopeMode("INHERIT");
        dto.setViewConfig(Map.of("table", Map.of("stripe", true)));
        dto.setSelectionConfig(Map.of("selectionMode", "NONE", "valueField", "id", "returnMappings", List.of()));
        EntityListField legacy = publishedField("published-legacy", "legacyProject", 9, 1_000_000L);
        legacy.setFieldName("项目扩展列");
        legacy.setDataSourceType("PROJECT_CUSTOM_FIELD");
        legacy.setDataSourceConfig("{ \"projectField\": \"summary\" }");
        legacy.setColumnConfig("{ \"minWidth\": 120 }");
        legacy.setQueryConfig(" ");
        legacy.setRenderConfig("{ \"emptyText\": \"--\" }");
        EntityListField template = publishedField("published-template", "summary", 3, 2_000_000L);
        template.setDataSourceType("FIELD_TEMPLATE");
        template.setDataSourceConfig("{ \"template\": \"${code} - ${name}\" }");
        dto.setFields(List.of(legacy, template));
        return dto;
    }

    private EntityListField publishedField(String id, String fieldCode, int sortOrder, long orderKey) {
        EntityListField field = new EntityListField();
        field.setId(id);
        field.setListConfigId("list-1");
        field.setFieldId("virtual_" + fieldCode);
        field.setFieldCode(fieldCode);
        field.setFieldName(fieldCode);
        field.setSortOrder(sortOrder);
        field.setOrderKey(orderKey);
        field.setRevision(7);
        field.setWidth(180);
        field.setShowInList(true);
        field.setIsQuery(false);
        field.setDeleted(0);
        return field;
    }

    /**
     * 真实服务与校验器只替换持久层等外部依赖；字段集合模拟删除和重建，
     * 用于检查公开撤销入口返回的结果确实来自恢复后的字段，而不是预制 DTO。
     */
    private static class Context {
        private final EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        private final EntityListFieldMapper fieldMapper = mock(EntityListFieldMapper.class);
        private final EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        private final EntityListActionConfigService actions = mock(EntityListActionConfigService.class);
        private final EntityPermissionCatalogService permissions = mock(EntityPermissionCatalogService.class);
        private final EntityUiConfigurationPolicy accessPolicy = mock(EntityUiConfigurationPolicy.class);
        private final UiConfigInterfaceReferenceValidator interfaceReferences = mock(UiConfigInterfaceReferenceValidator.class);
        private final EntityListConfigService service;

        private Context() {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
            EntityFieldMapper entityFields = mock(EntityFieldMapper.class);
            EntityListConfigurationValidator validator = new EntityListConfigurationValidator(
                    new StructuredConfigValidator(objectMapper), codec,
                    new ListFieldDataProviderRegistry(List.of(), objectMapper), entityFields, interfaceReferences);
            service = new EntityListConfigService(configMapper, fieldMapper, definitionMapper,
                    entityFields, mock(SystemEntityFieldPolicy.class), actions, permissions,
                    mock(EntityActionCapabilityService.class), validator, mock(CurrentUserRoleService.class),
                    accessPolicy, codec, mock(EntityListRelationalConfigService.class));
            EntityListConfig current = new EntityListConfig();
            current.setId("list-1");
            current.setEntityId("entity-1");
            current.setEntityCode("project");
            current.setListKey("default");
            current.setListName("草稿列表");
            current.setRevision(5);
            current.setActiveReleaseId("release-1");
            current.setPublishedVersion(2);
            current.setDeleted(0);
            AtomicReference<EntityListConfig> persisted = new AtomicReference<>(current);
            when(configMapper.selectByIdForUpdate("list-1")).thenReturn(current);
            when(configMapper.selectById("list-1")).thenAnswer(invocation -> persisted.get());
            when(configMapper.update(isNull(), any())).thenReturn(1);
            doAnswer(invocation -> {
                persisted.set(invocation.getArgument(0));
                return null;
            }).when(actions).synchronizeRelationalConfigForRelease(any());

            EntityListField draft = new EntityListField();
            draft.setId("draft-only");
            draft.setFieldCode("draftOnly");
            List<EntityListField> fields = new ArrayList<>(List.of(draft));
            when(fieldMapper.findByListConfigId("list-1")).thenAnswer(invocation -> List.copyOf(fields));
            when(fieldMapper.findAllByListConfigIdForUpdate("list-1"))
                    .thenAnswer(invocation -> List.copyOf(fields));
            when(fieldMapper.selectById(any())).thenAnswer(invocation -> fields.stream()
                    .filter(field -> field.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
            doAnswer(invocation -> {
                fields.clear();
                return null;
            }).when(fieldMapper).deleteByListConfigId("list-1");
            doAnswer(invocation -> {
                fields.add(invocation.getArgument(0));
                return 1;
            }).when(fieldMapper).insert(any(EntityListField.class));
        }

        private void verifyNoWrites() {
            verify(configMapper, never()).update(any(), any());
            verify(configMapper, never()).insert(any(EntityListConfig.class));
            verifyNoChildWrites();
        }

        private void verifyNoChildWrites() {
            verify(fieldMapper, never()).deleteByListConfigId(any());
            verify(fieldMapper, never()).insert(any(EntityListField.class));
            verify(fieldMapper, never()).update(any(), any());
            verify(actions, never()).deleteRelationalConfig(any());
            verify(actions, never()).synchronizeRelationalConfig(any());
            verify(actions, never()).synchronizeRelationalConfigForRelease(any());
            verifyNoInteractions(permissions);
        }
    }
}
