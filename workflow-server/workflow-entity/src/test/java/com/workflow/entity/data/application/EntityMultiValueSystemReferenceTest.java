package com.workflow.entity.data.application;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityMultiValueSystemReferenceTest {

    @Test
    void enrichesMultiUserReferenceWithoutUsingDynamicSystemTableAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        DynamicTableService dynamicTableService =
                mock(DynamicTableService.class);
        EntityPhysicalTableResolver tableResolver =
                mock(EntityPhysicalTableResolver.class);
        SystemEntityFieldPolicy systemFieldPolicy =
                mock(SystemEntityFieldPolicy.class);
        EntityMultiValueRuntimeService service =
                new EntityMultiValueRuntimeService(
                        jdbcTemplate,
                        fieldMapper,
                        definitionMapper,
                        dynamicTableService,
                        tableResolver,
                        systemFieldPolicy);

        EntityDefinition purchaseOrder = definition(
                "entity-purchase", "purchase_order",
                EntityDefinition.StorageMode.DYNAMIC,
                "biz_purchase_order");
        EntityDefinition systemUser = definition(
                "entity-user", "sys_user",
                EntityDefinition.StorageMode.SYSTEM,
                "sys_user");
        EntityField reviewers = new EntityField();
        reviewers.setEntityId(purchaseOrder.getId());
        reviewers.setFieldCode("reviewers");
        reviewers.setFieldType(EntityField.FieldType.MULTI_REFERENCE);
        reviewers.setRefEntityId(systemUser.getId());
        when(fieldMapper.findByEntityId(purchaseOrder.getId()))
                .thenReturn(List.of(reviewers));
        when(definitionMapper.selectById(systemUser.getId()))
                .thenReturn(systemUser);
        when(dynamicTableService.getMultiValueTableName(
                purchaseOrder.getEntityCode()))
                .thenReturn("biz_purchase_order_multi");
        when(systemFieldPolicy.isSupportedEntity("sys_user"))
                .thenReturn(true);
        when(systemFieldPolicy.displayField("sys_user"))
                .thenReturn("nickname");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.startsWith("SELECT record_id")) {
                        return List.of(
                                Map.of(
                                        "record_id", "record-1",
                                        "field_code", "reviewers",
                                        "target_entity_id", "old-target",
                                        "target_record_id", "user-collision",
                                        "sort_order", 0),
                                Map.of(
                                        "record_id", "record-1",
                                        "field_code", "reviewers",
                                        "target_entity_id", "entity-user",
                                        "target_record_id", "user-1",
                                        "sort_order", 1));
                    }
                    if (sql.contains("FROM sys_user")) {
                        return List.of(Map.of("display_name", "张三"));
                    }
                    return List.of();
                });
        EntityDataDTO record = new EntityDataDTO();
        record.setId("record-1");
        record.setData(new LinkedHashMap<>());

        service.enrich(purchaseOrder, List.of(record));

        assertEquals(List.of("user-1"),
                record.getData().get("reviewers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options =
                (List<Map<String, Object>>) record.getExtData()
                        .get("reviewersOptions");
        assertEquals("张三", options.get(0).get("label"));
        assertEquals(1, options.size(),
                "字段切换目标实体后的旧侧表行不得按 sys_user 回填");
        verify(tableResolver, never()).resolve(systemUser);
    }

    private EntityDefinition definition(
            String id,
            String code,
            EntityDefinition.StorageMode storageMode,
            String physicalTable) {
        EntityDefinition definition = new EntityDefinition();
        definition.setId(id);
        definition.setEntityCode(code);
        definition.setStorageMode(storageMode);
        definition.setPhysicalTableName(physicalTable);
        return definition;
    }
}
