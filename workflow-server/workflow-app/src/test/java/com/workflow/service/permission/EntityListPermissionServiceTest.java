package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.EntityListPermissionSaveRequest;
import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListPermission;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListPermissionMapper;
import com.workflow.mapper.EntityStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityListPermissionServiceTest {

    private final EntityListPermissionMapper permissionMapper =
            mock(EntityListPermissionMapper.class);
    private final EntityDefinitionMapper definitionMapper =
            mock(EntityDefinitionMapper.class);
    private final EntityListConfigMapper listConfigMapper =
            mock(EntityListConfigMapper.class);
    private final EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
    private final EntityStatusMapper statusMapper = mock(EntityStatusMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private EntityListPermissionService service;

    @BeforeEach
    void setUp() {
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("expense");
        when(definitionMapper.findByEntityCode("expense"))
                .thenReturn(Optional.of(definition));
        service = new EntityListPermissionService(
                permissionMapper,
                definitionMapper,
                listConfigMapper,
                objectMapper,
                new PermissionSqlBuilder(
                        definitionMapper,
                        fieldMapper,
                        statusMapper,
                        List.of()),
                List.of());
    }

    @Test
    void managementListIncludesDisabledRules() {
        EntityListPermission disabled = new EntityListPermission();
        disabled.setEnabled(0);
        when(permissionMapper.findByEntityCode("expense"))
                .thenReturn(List.of(disabled));

        List<EntityListPermission> result = service.findByEntityCode("expense");

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getEnabled());
        verify(permissionMapper).findByEntityCode("expense");
    }

    @Test
    void createDefaultsEmptyMatchConfigToAllUsers() throws Exception {
        EntityListPermissionSaveRequest request = baseRequest();
        request.setMatchConfig(null);
        request.setFilterConfig("{\"type\":\"PERSONAL\"}");

        service.create(request, "admin");

        ArgumentCaptor<EntityListPermission> captor =
                ArgumentCaptor.forClass(EntityListPermission.class);
        verify(permissionMapper).insert(captor.capture());
        MatchConfigDTO match = objectMapper.readValue(
                captor.getValue().getMatchConfig(),
                MatchConfigDTO.class);
        assertEquals("ALL_USERS", match.getConditions().get(0).getScopeType());
        assertTrue(captor.getValue().getCreatedAt() != null);
    }

    @Test
    void rejectsRemovedExpressionAndCustomSql() {
        EntityListPermissionSaveRequest request = baseRequest();
        request.setMatchConfig("""
                {"logic":"OR","conditions":[{"scopeType":"EXPRESSION"}]}
                """);
        request.setFilterConfig("""
                {"type":"CUSTOM_SQL","customSql":"1=1"}
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(request, "admin"));

        assertTrue(exception.getMessage().contains("自由表达式"));
    }

    private EntityListPermissionSaveRequest baseRequest() {
        EntityListPermissionSaveRequest request = new EntityListPermissionSaveRequest();
        request.setEntityCode("expense");
        request.setRuleName("测试规则");
        request.setPriority(10);
        request.setEnabled(1);
        request.setRuleEffect("ALLOW");
        request.setCombineMode("UNION");
        return request;
    }
}
