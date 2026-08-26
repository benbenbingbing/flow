package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityRelationProjectionReadPort.PredicateType;
import com.workflow.entity.data.application.EntityRelationProjectionReadPort.ProjectionPage;
import com.workflow.entity.data.application.EntityRelationProjectionReadPort.ProjectionQuery;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationProjectionMapper;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkField;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRelationProjectionReadAdapterTest {

    private EntityRelationProjectionMapper mapper;
    private DynamicTableService tableService;
    private EntityRelationProjectionReadAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = mock(EntityRelationProjectionMapper.class);
        tableService = mock(DynamicTableService.class);
        adapter = new EntityRelationProjectionReadAdapter(
                mapper, tableService);
        when(tableService.getTableName("requirement"))
                .thenReturn("biz_requirement");
        when(tableService.getMultiValueTableName("requirement"))
                .thenReturn("biz_requirement_multi");
    }

    @Test
    void readsOnlyPinnedScalarColumnAndForwardsScopeParameters() {
        LinkField project = scalar(
                "projectId", "pinned_project_fk", "project-id");
        when(mapper.count(anyMap())).thenReturn(1L);
        when(mapper.selectPage(anyMap())).thenReturn(List.of(Map.of(
                "record_id", "r-1",
                "link_0", "p-1")));

        ProjectionPage result = adapter.readPage(new ProjectionQuery(
                "requirement",
                List.of(project),
                PredicateType.LINK_IN,
                project,
                List.of("p-1"),
                scope(),
                1,
                20,
                100));

        assertEquals("p-1",
                result.rows().get(0).linkValues().get("projectId"));
        ArgumentCaptor<Map<String, Object>> parameters =
                ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectPage(parameters.capture());
        assertEquals("pinned_project_fk",
                parameters.getValue().get("predicateColumn"));
        assertEquals(Map.of("owner", "u-1"),
                parameters.getValue().get("permissionParameters"));
    }

    @Test
    void readsMultiReferenceFromSideTableAndRejectsOverflow() {
        LinkField customers = new LinkField(
                "customers",
                LinkValueType.MULTI_REFERENCE,
                null,
                "customer-id");
        when(mapper.count(anyMap())).thenReturn(1L);
        when(mapper.selectPage(anyMap())).thenReturn(List.of(Map.of(
                "record_id", "r-1")));
        when(mapper.selectMultiValues(anyMap())).thenReturn(List.of(
                multi("r-1", "customers", "customer-id", "c-1"),
                multi("r-1", "customers", "customer-id", "c-2")));

        ProjectionPage result = adapter.readPage(new ProjectionQuery(
                "requirement",
                List.of(customers),
                PredicateType.ID_IN,
                null,
                List.of("r-1"),
                scope(),
                1,
                20,
                2));
        assertEquals(List.of("c-1", "c-2"),
                result.rows().get(0).linkValues().get("customers"));

        when(mapper.selectMultiValues(anyMap())).thenReturn(List.of(
                multi("r-1", "customers", "customer-id", "c-1"),
                multi("r-1", "customers", "customer-id", "c-2"),
                multi("r-1", "customers", "customer-id", "c-3")));
        assertThrows(BusinessConflictException.class,
                () -> adapter.readPage(new ProjectionQuery(
                        "requirement",
                        List.of(customers),
                        PredicateType.ID_IN,
                        null,
                        List.of("r-1"),
                        scope(),
                        1,
                        20,
                        2)));
    }

    @Test
    void rejectsUntrustedScopeJoinAndConflictingPinnedField() {
        LinkField field = scalar(
                "projectId", "project_id", "project-id");
        DataScopePlan joined = new DataScopePlan(
                true,
                "1=1",
                Map.of(),
                List.of("JOIN attacker"),
                List.of(),
                "invalid",
                1);
        assertThrows(IllegalArgumentException.class,
                () -> adapter.readPage(new ProjectionQuery(
                        "requirement",
                        List.of(field),
                        PredicateType.ID_IN,
                        null,
                        List.of("r-1"),
                        joined,
                        1,
                        20,
                        100)));

        LinkField conflicting = scalar(
                "projectId", "attacker_column", "project-id");
        assertThrows(IllegalArgumentException.class,
                () -> adapter.readPage(new ProjectionQuery(
                        "requirement",
                        List.of(field, conflicting),
                        PredicateType.ID_IN,
                        null,
                        List.of("r-1"),
                        scope(),
                        1,
                        20,
                        100)));
    }

    private LinkField scalar(
            String code,
            String column,
            String targetEntityId) {
        return new LinkField(
                code,
                LinkValueType.SCALAR_REFERENCE,
                column,
                targetEntityId);
    }

    private DataScopePlan scope() {
        return new DataScopePlan(
                true,
                "owner_id = #{permissionParameters.owner}",
                Map.of("owner", "u-1"),
                List.of(),
                List.of("owner-policy"),
                "owner only",
                3);
    }

    private Map<String, Object> multi(
            String recordId,
            String fieldCode,
            String targetEntityId,
            String targetRecordId) {
        return Map.of(
                "record_id", recordId,
                "field_code", fieldCode,
                "target_entity_id", targetEntityId,
                "target_record_id", targetRecordId);
    }
}
