package com.workflow.service;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.service.listfield.ListFieldConditionEvaluator;
import com.workflow.service.listfield.ListFieldDataProvider;
import com.workflow.service.listfield.ListFieldDataProviderRegistry;
import com.workflow.service.permission.EntityActionCapabilityService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityDataListConfigServiceTest {

    @Test
    void virtualConditionsAreNotSentToDynamicSqlAndAreFilteredAfterEnrichment() {
        EntityDataDynamicService dynamicService = mock(EntityDataDynamicService.class);
        EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        EntityListFieldMapper fieldMapper = mock(EntityListFieldMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        ListFieldDataProviderRegistry providerRegistry = mock(ListFieldDataProviderRegistry.class);
        EntityActionCapabilityService capabilityService = mock(EntityActionCapabilityService.class);
        EntityDataListConfigService service = new EntityDataListConfigService(
                dynamicService,
                configMapper,
                fieldMapper,
                definitionMapper,
                providerRegistry,
                new ListFieldConditionEvaluator(),
                capabilityService);

        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(definition));

        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setListKey("default");
        when(configMapper.findByEntityIdAndListKey("entity-1", "default")).thenReturn(config);

        EntityListField virtualField = new EntityListField();
        virtualField.setFieldCode("summary");
        virtualField.setDataSourceType("CUSTOM_SUMMARY");
        virtualField.setShowInList(true);
        virtualField.setIsQuery(true);
        virtualField.setQueryType("LIKE");
        when(fieldMapper.findByListConfigId("list-1")).thenReturn(List.of(virtualField));

        EntityDataDTO first = row("1", "张三");
        EntityDataDTO second = row("2", "李四");
        Map<String, Object> baseCondition = Map.of("status", "DRAFT", "status_op", "EQ");
        when(dynamicService.findByCondition("expense", "list-1", baseCondition))
                .thenReturn(List.of(first, second));

        ListFieldDataProvider provider = mock(ListFieldDataProvider.class);
        when(providerRegistry.getProvider("CUSTOM_SUMMARY")).thenReturn(provider);
        org.mockito.Mockito.doAnswer(invocation -> {
            List<EntityDataDTO> records = invocation.getArgument(0);
            records.forEach(record -> {
                record.setExtData(new HashMap<>());
                record.getExtData().put("summary", record.getSubmitterName() + " 报销单");
            });
            return null;
        }).when(provider).enrich(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap());

        List<EntityDataDTO> result = service.findListWithConfig(
                "expense",
                "default",
                Map.of(
                        "status", "DRAFT",
                        "status_op", "EQ",
                        "summary", "张三",
                        "summary_op", "LIKE"));

        assertEquals(List.of(first), result);
        verify(dynamicService).findByCondition("expense", "list-1", baseCondition);
    }

    private EntityDataDTO row(String id, String submitterName) {
        EntityDataDTO row = new EntityDataDTO();
        row.setId(id);
        row.setSubmitterName(submitterName);
        return row;
    }
}
