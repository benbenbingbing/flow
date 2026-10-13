package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;

import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.contracts.entity.list.spi.ListFieldDataProvider;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体数据列表配置服务测试。
 *
 * <p>被测对象：{@link EntityDataListConfigService}，覆盖虚拟字段条件显式拒绝且不读取数据、
 * 基础条件使用权限感知的服务端分页等场景。
 */
class EntityDataListConfigServiceTest {

    /** 真实补数入口只接受展示结果，Provider 无法替换行身份、分页或保存的列配置。 */
    @Test
    void templateSnapshotsPreserveHostIdentityAndApplyDisplayValues() {
        var dynamicService = mock(EntityDataDynamicService.class);
        var configMapper = mock(EntityListConfigMapper.class);
        var fieldMapper = mock(EntityListFieldMapper.class);
        var definitionMapper = mock(EntityDefinitionMapper.class);
        var registry = mock(ListFieldDataProviderRegistry.class);
        var capabilities = mock(EntityActionCapabilityService.class);
        var published = mock(EntityListPublishedRuntimeService.class);
        var service = new EntityDataListConfigService(dynamicService, configMapper, fieldMapper,
                definitionMapper, registry, capabilities, published, mock(UiInterfaceExtensionService.class),
                new JsonDocumentCodec(new ObjectMapper()));

        var entity = new EntityDefinition();
        entity.setId("entity-1");
        var config = new EntityListConfig();
        config.setId("list-1");
        config.setListKey("default");
        var field = new EntityListField();
        field.setFieldCode("summary");
        field.setShowInList(true);
        field.setDataSourceType("FIELD_TEMPLATE");
        var source = row("record-1", "张三");
        source.setStatus("OPEN");
        source.setFormConfigHash("host-only");
        source.setCreateBy("creator");
        source.setCreateTime(java.time.LocalDateTime.of(2026, 9, 26, 12, 0));

        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(entity));
        when(configMapper.findByEntityIdAndListKey("entity-1", "default")).thenReturn(config);
        when(published.resolveConfig(config, null, java.util.Map.of())).thenReturn(config);
        when(fieldMapper.findByListConfigId("list-1")).thenReturn(List.of(field));
        when(published.resolveFields(config, List.of(field))).thenReturn(List.of(field));
        when(dynamicService.findPage("expense", "default", Map.of(), 2, 10, null, null))
                .thenReturn(new PageResult<>(List.of(source), 21, 2, 10));
        when(registry.getProvider("FIELD_TEMPLATE")).thenReturn(new ListFieldDataProvider() {
            public String getDataSourceType() { return "FIELD_TEMPLATE"; }

            public void enrich(
                    List<com.workflow.contracts.entity.list.model.ListFieldDataRecord> records,
                    List<com.workflow.contracts.entity.list.model.ListFieldDataConfig> fields,
                    Map<String, Object> context) {
                var record = records.get(0);
                assertEquals("creator", record.getCreateBy());
                assertEquals(source.getCreateTime(), record.getCreateTime());
                assertThrows(UnsupportedOperationException.class, records::clear);
                record.setId("replaced-id");
                record.setStatus("CHANGED");
                record.setData(Map.of("owner", "李四"));
                record.setExtData(Map.of("summary", "计算结果"));
                fields.get(0).setFieldCode("changed-config");
            }
        });

        var result = service.findPageWithConfig("expense", "default", Map.of(), 2, 10);
        assertEquals(21, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(List.of(source), result.getRecords());
        assertEquals("record-1", source.getId());
        assertEquals("OPEN", source.getStatus());
        assertEquals("host-only", source.getFormConfigHash());
        assertEquals("李四", source.getData().get("owner"));
        assertEquals("计算结果", source.getExtData().get("summary"));
        assertEquals("summary", field.getFieldCode());
        verify(capabilities).enrichRows("expense", config, List.of(source));
    }

    /** 只有明确的 INTERFACE 来源会调用接口，不再隐式覆盖实体值或调度自定义列 Provider。 */
    @Test
    void routesInterfaceColumnsWithoutCallingCustomProviders() {
        var dynamic = mock(EntityDataDynamicService.class);
        var configs = mock(EntityListConfigMapper.class);
        var fields = mock(EntityListFieldMapper.class);
        var definitions = mock(EntityDefinitionMapper.class);
        var registry = mock(ListFieldDataProviderRegistry.class);
        var published = mock(EntityListPublishedRuntimeService.class);
        var interfaces = mock(UiInterfaceExtensionService.class);
        var service = new EntityDataListConfigService(dynamic, configs, fields, definitions,
                registry, mock(EntityActionCapabilityService.class), published, interfaces,
                new JsonDocumentCodec(new ObjectMapper()));
        var entity = new EntityDefinition();
        entity.setId("entity-1");
        var config = new EntityListConfig();
        config.setId("list-1");
        config.setListKey("default");
        var calculated = new EntityListField();
        calculated.setListConfigId("list-1");
        calculated.setFieldId("virtual_summary");
        calculated.setFieldCode("summary");
        calculated.setShowInList(true);
        calculated.setDataSourceType("INTERFACE");
        calculated.setInterfaceExtensionId("column-interface");
        var custom = new EntityListField();
        custom.setFieldCode("oldCustom");
        custom.setShowInList(true);
        custom.setDataSourceType("CUSTOM_SUMMARY");
        var implicit = new EntityListField();
        implicit.setFieldCode("name");
        implicit.setShowInList(true);
        implicit.setDataSourceType("ENTITY_FIELD");
        implicit.setInterfaceExtensionId("implicit-interface");
        var columns = List.of(calculated, custom, implicit);
        var source = row("record-1", "张三");
        when(definitions.findByEntityCode("expense")).thenReturn(Optional.of(entity));
        when(configs.findByEntityIdAndListKey("entity-1", "default")).thenReturn(config);
        when(published.resolveConfig(config, null, java.util.Map.of())).thenReturn(config);
        when(fields.findByListConfigId("list-1")).thenReturn(columns);
        when(published.resolveFields(config, columns)).thenReturn(columns);
        when(dynamic.findPage("expense", "default", Map.of(), 1, 10, null, null))
                .thenReturn(new PageResult<>(List.of(source), 1, 1, 10));
        when(interfaces.execute(eq("column-interface"), any())).thenReturn(Map.of("record-1", "接口计算值"));

        var result = service.findPageWithConfig("expense", "default", Map.of(), 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals("接口计算值", source.getExtData().get("summary"));
        assertEquals("张三", source.getSubmitterName());
        verify(interfaces).execute(eq("column-interface"), any());
        org.mockito.Mockito.verifyNoMoreInteractions(interfaces);
        verifyNoInteractions(registry);
    }

    /** 直接请求虚拟条件也必须在读取实体数据之前失败，不能依赖前端是否展示查询项 */
    @Test
    void virtualConditionsAreRejectedBeforeReadingData() {
        EntityDataDynamicService dynamicService = mock(EntityDataDynamicService.class);
        EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        EntityListFieldMapper fieldMapper = mock(EntityListFieldMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        ListFieldDataProviderRegistry providerRegistry = mock(ListFieldDataProviderRegistry.class);
        EntityActionCapabilityService capabilityService = mock(EntityActionCapabilityService.class);
        EntityListPublishedRuntimeService publishedRuntimeService =
                mock(EntityListPublishedRuntimeService.class);
        UiInterfaceExtensionService uiDataSourceService = mock(UiInterfaceExtensionService.class);
        EntityDataListConfigService service = new EntityDataListConfigService(
                dynamicService,
                configMapper,
                fieldMapper,
                definitionMapper,
                providerRegistry,
                capabilityService,
                publishedRuntimeService,
                uiDataSourceService,
                new JsonDocumentCodec(new ObjectMapper()));

        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(definition));

        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setListKey("default");
        when(configMapper.findByEntityIdAndListKey("entity-1", "default")).thenReturn(config);
        when(publishedRuntimeService.resolveConfig(config, null, java.util.Map.of()))
                .thenReturn(config);

        EntityListField virtualField = new EntityListField();
        virtualField.setFieldCode("summary");
        virtualField.setDataSourceType("CUSTOM_SUMMARY");
        virtualField.setShowInList(true);
        virtualField.setIsQuery(true);
        virtualField.setQueryType("LIKE");
        when(fieldMapper.findByListConfigId("list-1")).thenReturn(List.of(virtualField));
        when(publishedRuntimeService.resolveFields(config, List.of(virtualField)))
                .thenReturn(List.of(virtualField));

        virtualField.setIsQuery(false);
        assertThrows(IllegalArgumentException.class, () -> service.findListWithConfig(
                "expense", "default", Map.of("summary", "张三", "summary_op", "LIKE")));
        verifyNoInteractions(dynamicService, providerRegistry, uiDataSourceService);
    }

    /** 测试基础条件使用权限感知的服务端分页：验证分页查询走 findPage 且对结果做权限增强 */
    @Test
    void baseConditionsUsePermissionAwareServerPagination() {
        EntityDataDynamicService dynamicService = mock(EntityDataDynamicService.class);
        EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        EntityListFieldMapper fieldMapper = mock(EntityListFieldMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        ListFieldDataProviderRegistry providerRegistry = mock(ListFieldDataProviderRegistry.class);
        EntityActionCapabilityService capabilityService = mock(EntityActionCapabilityService.class);
        EntityListPublishedRuntimeService publishedRuntimeService =
                mock(EntityListPublishedRuntimeService.class);
        UiInterfaceExtensionService uiDataSourceService = mock(UiInterfaceExtensionService.class);
        EntityDataListConfigService service = new EntityDataListConfigService(
                dynamicService,
                configMapper,
                fieldMapper,
                definitionMapper,
                providerRegistry,
                capabilityService,
                publishedRuntimeService,
                uiDataSourceService,
                new JsonDocumentCodec(new ObjectMapper()));

        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(definition));
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setListKey("default");
        config.setViewConfig("{\"table\":{\"defaultSortField\":\"amount\",\"defaultSortDirection\":\"DESC\"}}");
        when(configMapper.findByEntityIdAndListKey("entity-1", "default")).thenReturn(config);
        when(publishedRuntimeService.resolveConfig(config, null, java.util.Map.of()))
                .thenReturn(config);
        when(fieldMapper.findByListConfigId("list-1")).thenReturn(List.of());
        when(publishedRuntimeService.resolveFields(config, List.of())).thenReturn(List.of());

        EntityDataDTO row = row("11", "张三");
        Map<String, Object> condition = Map.of("status", "OPEN", "status_op", "EQ");
        when(dynamicService.findPage(
                "expense",
                "default",
                condition,
                2,
                10, "amount", "DESC")).thenReturn(new PageResult<>(List.of(row), 21, 2, 10));

        PageResult<EntityDataDTO> result = service.findPageWithConfig(
                "expense",
                "default",
                condition,
                2,
                10);

        assertEquals(21, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(List.of(row), result.getRecords());
        verify(dynamicService).findPage(
                "expense",
                "default",
                condition,
                2,
                10, "amount", "DESC");
        verify(capabilityService).enrichRows("expense", config, List.of(row));
    }

    /** 旧发布版仍配置虚拟查询时明确失败，管理员需取消配置后重新发布。 */
    @Test
    void legacyVirtualQueryConfigurationCannotTriggerFullListPagination() {
        EntityDataDynamicService dynamicService = mock(EntityDataDynamicService.class);
        EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        EntityListFieldMapper fieldMapper = mock(EntityListFieldMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        ListFieldDataProviderRegistry providerRegistry = mock(ListFieldDataProviderRegistry.class);
        EntityListPublishedRuntimeService publishedRuntimeService =
                mock(EntityListPublishedRuntimeService.class);
        EntityDataListConfigService service = new EntityDataListConfigService(
                dynamicService, configMapper, fieldMapper, definitionMapper,
                providerRegistry,
                mock(EntityActionCapabilityService.class), publishedRuntimeService,
                mock(UiInterfaceExtensionService.class),
                new JsonDocumentCodec(new ObjectMapper()));

        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        when(definitionMapper.findByEntityCode("expense"))
                .thenReturn(Optional.of(definition));
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setListKey("default");
        config.setViewConfig("{\"table\":{\"defaultSortField\":\"amount\",\"defaultSortDirection\":\"DESC\"}}");
        when(configMapper.findByEntityIdAndListKey("entity-1", "default"))
                .thenReturn(config);
        when(publishedRuntimeService.resolveConfig(config, null, java.util.Map.of()))
                .thenReturn(config);

        EntityListField virtualField = new EntityListField();
        virtualField.setFieldCode("summary");
        virtualField.setDataSourceType("CUSTOM_SUMMARY");
        virtualField.setShowInList(true);
        virtualField.setIsQuery(true);
        virtualField.setQueryType("LIKE");
        when(fieldMapper.findByListConfigId("list-1"))
                .thenReturn(List.of(virtualField));
        when(publishedRuntimeService.resolveFields(config, List.of(virtualField)))
                .thenReturn(List.of(virtualField));

        assertThrows(IllegalArgumentException.class, () -> service.findPageWithConfig(
                "expense", "default", Map.of(), 1, 20));
        verifyNoInteractions(dynamicService, providerRegistry);
    }

    /** 显式列表不存在时必须失败关闭，不能退回不带列表约束的通用查询。 */
    @Test
    void explicitMissingListDoesNotFallBackToGenericListQuery() {
        ServiceFixture fixture = fixtureWithoutList();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().findListWithConfig(
                        "expense",
                        "missing",
                        Map.of()));

        assertEquals("列表不存在或尚未发布: missing", exception.getMessage());
        verifyNoInteractions(fixture.dynamicService());
    }

    /** 显式列表分页查询不存在时同样必须失败关闭。 */
    @Test
    void explicitMissingListDoesNotFallBackToGenericPageQuery() {
        ServiceFixture fixture = fixtureWithoutList();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().findPageWithConfig(
                        "expense",
                        "missing",
                        Map.of(),
                        1,
                        10));

        assertEquals("列表不存在或尚未发布: missing", exception.getMessage());
        verifyNoInteractions(fixture.dynamicService());
    }

    private ServiceFixture fixtureWithoutList() {
        EntityDataDynamicService dynamicService = mock(EntityDataDynamicService.class);
        EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        EntityListPublishedRuntimeService publishedRuntimeService =
                mock(EntityListPublishedRuntimeService.class);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        when(definitionMapper.findByEntityCode("expense"))
                .thenReturn(Optional.of(definition));
        when(configMapper.findByEntityIdAndListKey("entity-1", "missing"))
                .thenReturn(null);

        EntityDataListConfigService service = new EntityDataListConfigService(
                dynamicService,
                configMapper,
                mock(EntityListFieldMapper.class),
                definitionMapper,
                mock(ListFieldDataProviderRegistry.class),
                mock(EntityActionCapabilityService.class),
                publishedRuntimeService,
                mock(UiInterfaceExtensionService.class),
                new JsonDocumentCodec(new ObjectMapper()));
        return new ServiceFixture(service, dynamicService);
    }

    private record ServiceFixture(
            EntityDataListConfigService service,
            EntityDataDynamicService dynamicService) {
    }

    /** 构造带 id 与提交人名的实体数据 DTO */
    private EntityDataDTO row(String id, String submitterName) {
        EntityDataDTO row = new EntityDataDTO();
        row.setId(id);
        row.setSubmitterName(submitterName);
        return row;
    }
}
