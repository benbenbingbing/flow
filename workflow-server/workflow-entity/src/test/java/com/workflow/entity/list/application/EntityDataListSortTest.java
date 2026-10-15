package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.PageResult;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 验证排序作用于数据库分页并保留发布版本边界，不对当前页进行内存重排。 */
class EntityDataListSortTest {
    private final EntityDataDynamicService dynamic = mock(EntityDataDynamicService.class);
    private final EntityListPublishedRuntimeService published = mock(EntityListPublishedRuntimeService.class);
    private final EntityListConfig config = new EntityListConfig();
    private final EntityListField field = new EntityListField();
    private EntityDataListConfigService service;

    @BeforeEach
    void setUp() {
        service = new EntityDataListConfigService(dynamic, mock(EntityListConfigMapper.class),
                mock(EntityListFieldMapper.class), mock(EntityDefinitionMapper.class),
                mock(ListFieldDataProviderRegistry.class), mock(EntityActionCapabilityService.class),
                published, mock(UiInterfaceExtensionService.class), new JsonDocumentCodec(new ObjectMapper()));
        config.setId("list");
        config.setListKey("main");
        config.setViewConfig("{\"table\":{\"defaultSortField\":\"createdAt\",\"defaultSortDirection\":\"desc\"}}");
        field.setFieldCode("amount");
        field.setShowInList(true);
        field.setColumnConfig("{\"sortable\":true}");
        when(published.resolveFields(eq(config), any())).thenReturn(List.of(field));
    }

    @Test
    void requestedSortOverridesDefaultBeforeDatabasePagination() {
        Map<String, Object> filters = Map.of("status", "ACTIVE");
        when(dynamic.findPage("expense", "main", filters, 3, 20, "amount", "asc"))
                .thenReturn(new PageResult<>(List.of(), 80, 3, 20));

        var page = service.findPageWithResolvedConfig("expense", "main", config, filters, 3, 20, "amount", "asc");

        assertEquals(80, page.getTotal());
        assertEquals(3, page.getPageNum());
        verify(dynamic).findPage("expense", "main", filters, 3, 20, "amount", "asc");
    }

    @Test
    void absentHeaderSortPreservesConfiguredDefaultEvenWithoutSortableColumn() {
        when(dynamic.findPage("expense", "main", Map.of(), 1, 20, "createdAt", "desc"))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        service.findPageWithResolvedConfig("expense", "main", config, Map.of(), 1, 20, null, null);

        verify(dynamic).findPage("expense", "main", Map.of(), 1, 20, "createdAt", "desc");
    }

    @Test
    void draftSortFlagCannotEnableSortingInPublishedSnapshot() {
        field.setColumnConfig("{\"sortable\":false}");

        assertThrows(IllegalArgumentException.class, () -> service.findPageWithResolvedConfig(
                "expense", "main", config, Map.of(), 1, 20, "amount", "asc"));

        verifyNoInteractions(dynamic);
    }
}
