package com.workflow.service.permission;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.permission.application.EntityListScopeAuditService;
import com.workflow.entity.permission.application.EntityListScopeService;
import com.workflow.entity.permission.application.PermissionRuleMatcher;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeBindingMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopePolicyMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeReleaseMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.permission.api.response.EntityListScopePolicyDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopePolicy;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 实体列表作用域服务测试。
 *
 * <p>被测对象：{@link EntityListScopeService}，覆盖保存遗留策略时清除审核标志并创建草稿、
 * 发布时拒绝未审核的遗留规则等场景。
 */
class EntityListScopeServiceTest {

    /** 测试保存遗留策略清除审核标志并创建草稿：验证保存后状态为 DRAFT、审核标志为 0 且触发过滤校验 */
    @Test
    void savingLegacyPolicyClearsReviewFlagAndCreatesDraft() {
        EntityListScopePolicyMapper policyMapper = mock(EntityListScopePolicyMapper.class);
        EntityListScopeBindingMapper bindingMapper = mock(EntityListScopeBindingMapper.class);
        EntityListScopeReleaseMapper releaseMapper = mock(EntityListScopeReleaseMapper.class);
        EntityListConfigMapper listConfigMapper = mock(EntityListConfigMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        PermissionSqlBuilder sqlBuilder = mock(PermissionSqlBuilder.class);
        PermissionRuleMatcher matcher = mock(PermissionRuleMatcher.class);
        EntityListScopeAuditService audit = mock(EntityListScopeAuditService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EntityDefinitionAccessPolicy accessPolicy = mock(EntityDefinitionAccessPolicy.class);
        EntityListScopeService service = new EntityListScopeService(
                policyMapper,
                bindingMapper,
                releaseMapper,
                listConfigMapper,
                definitionMapper,
                sqlBuilder,
                matcher,
                objectMapper,
                audit,
                accessPolicy);

        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("expense");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(entity));
        when(accessPolicy.requireDynamicByCode("expense")).thenReturn(entity);
        when(policyMapper.insert(any(EntityListScopePolicy.class))).thenAnswer(invocation -> {
            EntityListScopePolicy policy = invocation.getArgument(0);
            policy.setId("policy-1");
            return 1;
        });
        when(policyMapper.selectById("policy-1")).thenAnswer(invocation -> {
            EntityListScopePolicy policy = new EntityListScopePolicy();
            policy.setId("policy-1");
            policy.setEntityCode("expense");
            policy.setPolicyKey("personal");
            policy.setPolicyName("本人数据");
            policy.setFilterConfig("{\"version\":1,\"type\":\"PERSONAL\"}");
            policy.setStatus("DRAFT");
            policy.setEnabled(1);
            policy.setVersion(1);
            policy.setReviewRequired(0);
            return policy;
        });

        EntityListScopePolicyDTO request = new EntityListScopePolicyDTO();
        request.setEntityCode("expense");
        request.setPolicyKey("personal");
        request.setPolicyName("本人数据");
        request.setEnabled(1);
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("PERSONAL");
        request.setFilterConfig(filter);

        EntityListScopePolicyDTO saved = service.savePolicy(null, request);

        assertEquals("DRAFT", saved.getStatus());
        assertEquals(0, saved.getReviewRequired());
        verify(sqlBuilder).validateFilter("expense", filter);
    }

    /** 测试发布拒绝未审核的遗留规则：验证存在需审核策略时发布抛出 IllegalStateException */
    @Test
    void publishRejectsUnreviewedLegacyRules() {
        EntityListScopePolicyMapper policyMapper = mock(EntityListScopePolicyMapper.class);
        EntityListScopeBindingMapper bindingMapper = mock(EntityListScopeBindingMapper.class);
        EntityListScopeReleaseMapper releaseMapper = mock(EntityListScopeReleaseMapper.class);
        EntityListConfigMapper listConfigMapper = mock(EntityListConfigMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        EntityDefinitionAccessPolicy accessPolicy = mock(EntityDefinitionAccessPolicy.class);
        EntityListScopeService service = new EntityListScopeService(
                policyMapper,
                bindingMapper,
                releaseMapper,
                listConfigMapper,
                definitionMapper,
                mock(PermissionSqlBuilder.class),
                mock(PermissionRuleMatcher.class),
                new ObjectMapper(),
                mock(EntityListScopeAuditService.class),
                accessPolicy);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("expense");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(entity));
        when(accessPolicy.requireDynamicByCodeForUpdate("expense"))
                .thenReturn(entity);
        EntityListScopePolicy policy = new EntityListScopePolicy();
        policy.setReviewRequired(1);
        when(policyMapper.findByEntityCode("expense")).thenReturn(List.of(policy));

        assertThrows(
                IllegalStateException.class,
                () -> service.publish("expense", "test"));
    }

    @Test
    void publishDoesNotRequireEntityDefaultAllow() {
        EntityListScopePolicyMapper policyMapper = mock(EntityListScopePolicyMapper.class);
        EntityListScopeBindingMapper bindingMapper = mock(EntityListScopeBindingMapper.class);
        EntityListScopeReleaseMapper releaseMapper = mock(EntityListScopeReleaseMapper.class);
        EntityListConfigMapper listConfigMapper = mock(EntityListConfigMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        EntityDefinitionAccessPolicy accessPolicy = mock(EntityDefinitionAccessPolicy.class);
        EntityListScopeService service = new EntityListScopeService(
                policyMapper,
                bindingMapper,
                releaseMapper,
                listConfigMapper,
                definitionMapper,
                mock(PermissionSqlBuilder.class),
                mock(PermissionRuleMatcher.class),
                new ObjectMapper(),
                mock(EntityListScopeAuditService.class),
                accessPolicy);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("expense");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(accessPolicy.requireDynamicByCodeForUpdate("expense")).thenReturn(entity);
        when(policyMapper.findByEntityCode("expense")).thenReturn(List.of());
        when(bindingMapper.findByEntityCode("expense")).thenReturn(List.of());
        when(listConfigMapper.findByEntityCode("expense")).thenReturn(List.of());
        when(releaseMapper.findMaxVersion("expense")).thenReturn(0);

        assertDoesNotThrow(() -> service.publish("expense", "empty-catalog"));
        verify(releaseMapper).insert(any(com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeRelease.class));
        verify(listConfigMapper, never()).updateById(any(com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig.class));
    }

    @Test
    void deletePolicyFailsWhenStillBound() {
        EntityListScopePolicyMapper policyMapper = mock(EntityListScopePolicyMapper.class);
        EntityListScopeBindingMapper bindingMapper = mock(EntityListScopeBindingMapper.class);
        EntityListScopeService service = new EntityListScopeService(
                policyMapper,
                bindingMapper,
                mock(EntityListScopeReleaseMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionMapper.class),
                mock(PermissionSqlBuilder.class),
                mock(PermissionRuleMatcher.class),
                new ObjectMapper(),
                mock(EntityListScopeAuditService.class),
                mock(EntityDefinitionAccessPolicy.class));
        EntityListScopePolicy policy = new EntityListScopePolicy();
        policy.setId("policy-1");
        when(policyMapper.selectById("policy-1")).thenReturn(policy);
        when(bindingMapper.selectCount(any())).thenReturn(1L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.deletePolicy("policy-1"));
        assertTrue(error.getMessage().contains("列表设置"));
    }

    @Test
    void replaceListBindingsPublishesActiveSnapshot() {
        EntityListScopePolicyMapper policyMapper = mock(EntityListScopePolicyMapper.class);
        EntityListScopeBindingMapper bindingMapper = mock(EntityListScopeBindingMapper.class);
        EntityListScopeReleaseMapper releaseMapper = mock(EntityListScopeReleaseMapper.class);
        EntityListConfigMapper listConfigMapper = mock(EntityListConfigMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        EntityDefinitionAccessPolicy accessPolicy = mock(EntityDefinitionAccessPolicy.class);
        PermissionRuleMatcher matcher = mock(PermissionRuleMatcher.class);
        EntityListScopeService service = new EntityListScopeService(
                policyMapper,
                bindingMapper,
                releaseMapper,
                listConfigMapper,
                definitionMapper,
                mock(PermissionSqlBuilder.class),
                matcher,
                new ObjectMapper(),
                mock(EntityListScopeAuditService.class),
                accessPolicy);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("expense");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(accessPolicy.requireDynamicByCode("expense")).thenReturn(entity);
        when(accessPolicy.requireDynamicByCodeForUpdate("expense")).thenReturn(entity);
        com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig list =
                new com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig();
        list.setListKey("all");
        when(listConfigMapper.findByEntityCodeAndListKey("expense", "all"))
                .thenReturn(list);
        when(listConfigMapper.findByEntityCode("expense")).thenReturn(List.of(list));
        when(bindingMapper.selectList(any())).thenReturn(List.of());
        when(bindingMapper.findByEntityCode("expense")).thenReturn(List.of());
        when(policyMapper.findByEntityCode("expense")).thenReturn(List.of());
        when(releaseMapper.findMaxVersion("expense")).thenReturn(2);

        service.replaceListBindings("expense", "all", List.of());

        verify(releaseMapper).insert(any(
                com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeRelease.class));
    }
}
