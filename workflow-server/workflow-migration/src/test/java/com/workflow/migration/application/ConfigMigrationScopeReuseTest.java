package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.permission.application.EntityListScopeService;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeBindingMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopePolicyMapper;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeBinding;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 相同方案与绑定再次应用时复用已有本地 ID，不累积重复的权限记录。 */
class ConfigMigrationScopeReuseTest {
    @Test
    void repeatedApplicationReusesPolicyKeyAndEquivalentBinding() {
        EntityListScopePolicyMapper policies = mock(EntityListScopePolicyMapper.class);
        EntityListScopeBindingMapper bindings = mock(EntityListScopeBindingMapper.class);
        ConfigMigrationImportApplyService service = mock(ConfigMigrationImportApplyService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "listScopePolicyMapper", policies);
        ReflectionTestUtils.setField(service, "listScopeBindingMapper", bindings);
        ReflectionTestUtils.setField(service, "listScopeService", mock(EntityListScopeService.class));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper().findAndRegisterModules());
        EntityDefinition entity = new EntityDefinition(); entity.setEntityCode("expense");
        EntityListScopePolicy policy = new EntityListScopePolicy(); policy.setId("local-policy"); policy.setPolicyKey("reviewers"); policy.setEntityCode("expense");
        EntityListScopeBinding binding = new EntityListScopeBinding(); binding.setId("local-binding"); binding.setEntityCode("expense"); binding.setPolicyId("local-policy");
        binding.setMatchConfig("{ \"conditions\": [{\"scopeType\":\"USER\",\"targetIds\":[\"local-user\"]}] }");
        when(policies.selectList(any())).thenReturn(List.of(policy));
        when(bindings.selectList(any())).thenReturn(List.of(binding));
        List<Map<String, Object>> incomingPolicy = List.of(Map.of("policyKey", "reviewers"));
        List<Map<String, Object>> incomingBinding = List.of(Map.of("policyKey", "reviewers", "matchConfig",
                "{\"conditions\":[{\"scopeType\":\"USER\",\"targetIds\":[\"local-user\"]}]}"));
        for (int count = 0; count < 2; count++) {
            ReflectionTestUtils.invokeMethod(service, "applyDataScopes", entity, incomingPolicy, incomingBinding);
        }
        verify(policies, times(2)).updateById(argThat((EntityListScopePolicy value) -> "local-policy".equals(value.getId())));
        verify(policies, never()).insert(any(EntityListScopePolicy.class));
        verify(bindings, never()).insert(any(EntityListScopeBinding.class));
        verify(bindings, never()).deleteById(anyString());
        verify(policies, never()).deleteById(anyString());
    }
}
