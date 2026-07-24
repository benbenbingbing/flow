package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.EntityListScopePolicyDTO;
import com.workflow.dto.permission.FilterConfigDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListScopePolicy;
import com.workflow.mapper.*;
import com.workflow.service.EntityDefinitionAccessPolicy;
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
        when(accessPolicy.requireDynamicByCode("expense")).thenReturn(entity);
        EntityListScopePolicy policy = new EntityListScopePolicy();
        policy.setReviewRequired(1);
        when(policyMapper.findByEntityCode("expense")).thenReturn(List.of(policy));

        assertThrows(
                IllegalStateException.class,
                () -> service.publish("expense", "test"));
    }
}
