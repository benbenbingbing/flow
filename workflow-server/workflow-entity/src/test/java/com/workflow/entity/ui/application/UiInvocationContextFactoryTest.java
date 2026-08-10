package com.workflow.entity.ui.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.ui.EntityInvocationContext;
import com.workflow.contracts.ui.FormInvocationContext;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiInvocationContextFactoryTest {

    @Test
    void buildsTrustedFormContextFromServerResolvedOwners() {
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFormMapper formMapper =
                mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper =
                mock(EntityListConfigMapper.class);
        UiInvocationContextFactory factory =
                new UiInvocationContextFactory(
                        definitionMapper,
                        formMapper,
                        listMapper);

        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-a");
        entity.setEntityCode("expense");
        entity.setEntityName("费用");
        entity.setStorageMode(
                EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.selectById("entity-a"))
                .thenReturn(entity);

        EntityForm form = new EntityForm();
        form.setId("form-a");
        form.setEntityId("entity-a");
        form.setFormKey("expense-edit");
        form.setFormName("费用编辑");
        when(formMapper.selectById("form-a"))
                .thenReturn(form);

        SysUser user = new SysUser();
        user.setId("user-a");
        user.setUsername("tester");
        user.setOrgId("org-a");
        user.setDeptId("dept-a");
        UiDataSourceExecutionAuthorization authorization =
                new UiDataSourceExecutionAuthorization(
                        false,
                        "FORM",
                        "form-a",
                        "release-a",
                        3,
                        "$.release.form[1]",
                        "FIELD_OPTIONS",
                        "entity-a",
                        "expense",
                        null,
                        user,
                        new DataScopePlan(
                                true,
                                "1=1",
                                Map.of(),
                                List.of(),
                                List.of(),
                                "allowed",
                                7),
                        Map.of(),
                        null);

        UiDataSourceDefinition definition =
                new UiDataSourceDefinition();
        definition.setId("service-a");
        definition.setOperationCode("queryApprovers");
        definition.setOperationContextType("FORM");

        UiDataSourceExecuteRequest request =
                new UiDataSourceExecuteRequest();
        request.setTargetType("FIELD");
        request.setTargetKey("approverId");
        request.setInput(Map.of(
                "mode", "edit",
                "recordId", "record-a",
                "parent", Map.of(
                        "recordId", "parent-a"),
                "row", Map.of(
                        "index", 2),
                "requestId", "forged-request",
                "entityCode", "forged-entity",
                "userId", "forged-user"));

        UiInvocationContext raw = factory.create(
                definition,
                authorization,
                request);
        FormInvocationContext context =
                (FormInvocationContext) raw;

        assertEquals("expense", context.entity().code());
        assertEquals("费用编辑", context.formName());
        assertEquals("approverId", context.fieldCode());
        assertEquals("user-a", context.common().userId());
        assertEquals("org-a", context.common().organizationId());
        assertEquals("org-a", context.common().tenantId());
        assertEquals("parent-a", context.parentRecordId());
        assertEquals("2", context.rowKey());
        assertNotEquals(
                "forged-request",
                context.common().requestId());
    }

    @Test
    void buildsEntityOperationFromServerOnlyField() {
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFormMapper formMapper =
                mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper =
                mock(EntityListConfigMapper.class);
        UiInvocationContextFactory factory =
                new UiInvocationContextFactory(
                        definitionMapper,
                        formMapper,
                        listMapper);

        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-a");
        entity.setEntityCode("expense");
        entity.setEntityName("费用");
        entity.setStorageMode(
                EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.selectById("entity-a"))
                .thenReturn(entity);

        SysUser user = new SysUser();
        user.setId("user-a");
        user.setUsername("tester");
        UiDataSourceExecutionAuthorization authorization =
                new UiDataSourceExecutionAuthorization(
                        false,
                        "ENTITY",
                        "entity-a",
                        null,
                        5,
                        "$.release.entity.steps[0]",
                        "ENTITY_MUTATION_PREPARE",
                        "entity-a",
                        "expense",
                        null,
                        user,
                        new DataScopePlan(
                                true,
                                "1=1",
                                Map.of(),
                                List.of(),
                                List.of(),
                                "allowed",
                                9),
                        Map.of(),
                        null);

        UiDataSourceDefinition definition =
                new UiDataSourceDefinition();
        definition.setId("service-a");
        definition.setOperationCode("validateExpense");
        definition.setOperationContextType("ENTITY");

        UiDataSourceExecuteRequest request =
                new UiDataSourceExecuteRequest();
        request.setServerEntityOperation("UPDATE");
        request.setInput(Map.of(
                "recordId", "record-a",
                "operation", "DELETE"));

        EntityInvocationContext context =
                (EntityInvocationContext) factory.create(
                        definition,
                        authorization,
                        request);

        assertEquals("UPDATE", context.operation());
        assertEquals("record-a", context.recordId());
        assertEquals("entity-a", context.common().ownerId());
    }
}
