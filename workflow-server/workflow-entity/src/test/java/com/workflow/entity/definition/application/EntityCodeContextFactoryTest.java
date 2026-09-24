package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.entity.data.application.*;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.code.*;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityCodeContextFactoryTest {
    @Test
    void passesLogicalFieldsAndTrustedActorWithoutQueryingNewRecord() {
        var snapshots = mock(EntityPublishedSnapshotService.class);
        var mapper = mock(EntityDataDynamicMapper.class);
        var tables = mock(DynamicTableService.class);
        var users = mock(SysUserService.class);
        var validation = mock(EntityDataMutationValidator.class);
        var rules = mock(EntityFieldValidationRuleService.class);
        var field = new EntityField(); field.setFieldCode("project_type"); field.setDbColumnName("project_type");
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(List.of(field));
        when(snapshots.getLatestByEntityCode("child")).thenReturn(snapshot);
        when(snapshots.getLatestByEntityCode("parent")).thenReturn(snapshot);
        when(tables.getTableName("parent")).thenReturn("wf_parent");
        when(mapper.selectById("wf_parent", "parent-1")).thenReturn(Map.of("id", "parent-1", "code", "P001", "project_type", "parent-type"));
        var user = new SysUser(); user.setDeptId("trusted-dept");
        when(users.getById("trusted-user")).thenReturn(user);
        var factory = new EntityCodeContextFactory(snapshots, new EntityRuntimeRecordMapper(new ObjectMapper()),
                mapper, tables, users, validation, rules);
        var context = EntityMutationContext.builder(EntityMutationSourceType.SYSTEM_TASK, "CREATE", "创建")
                .operator("trusted-user", "可信身份").trace("trace", "stable-request").build();
        UserContext.setCurrentUser("http-user", "HTTP 身份");
        try (var ignored = EntityCodeGenerationScope.open(context)) {
            var input = new EntityCodeGenerationInput("child", "new-child", Map.of("project_type", "A", "code", "FORGED", "dept_id", "forged-dept"),
                    "parent", "parent-1", "root/details/0", "FORGED");
            var generated = factory.create(input);
            assertEquals("new-child", generated.recordId());
            assertEquals("A", generated.data().get("project_type"));
            assertFalse(generated.data().containsKey("code"));
            assertEquals("trusted-user", generated.operatorId());
            assertEquals("trusted-dept", generated.deptId());
            assertEquals("P001", generated.parentData().get("code"));
            assertEquals("parent-type", generated.parentData().get("project_type"));
            assertEquals("stable-request:code:child:root/details/0", generated.idempotencyKey());
            verify(validation).validateGenerationInput("child", input.storageData());
            verify(mapper, never()).selectById(anyString(), eq("new-child"));
        } finally { UserContext.clear(); }
        assertNull(EntityCodeGenerationScope.current());
    }

    @Test
    void nestedMutationScopesRestorePreviousIdentityEvenAfterFailure() {
        var outer = EntityMutationContext.builder(EntityMutationSourceType.SYSTEM_TASK, "OUTER", "外层").build();
        var inner = EntityMutationContext.builder(EntityMutationSourceType.SYSTEM_TASK, "INNER", "内层").build();
        try (var ignored = EntityCodeGenerationScope.open(outer)) {
            assertThrows(IllegalStateException.class, () -> {
                try (var nested = EntityCodeGenerationScope.open(inner)) { throw new IllegalStateException("failed"); }
            });
            assertSame(outer, EntityCodeGenerationScope.current());
        }
        assertNull(EntityCodeGenerationScope.current());
    }
}
