package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.DataPermissionResult;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityListPermissionDelegateMapper;
import com.workflow.mapper.EntityListPermissionMapper;
import com.workflow.mapper.SysOrganizationMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataPermissionEngineTest {

    @Test
    void calculatePermissionDefaultsToCurrentUserWhenNoRulesExist() {
        EntityListPermissionMapper permissionMapper = mock(EntityListPermissionMapper.class);
        EntityListPermissionDelegateMapper delegateMapper = mock(EntityListPermissionDelegateMapper.class);
        SysOrganizationMapper orgMapper = mock(SysOrganizationMapper.class);
        DataPermissionEngine engine = new DataPermissionEngine(
                permissionMapper,
                delegateMapper,
                new ObjectMapper(),
                new PermissionRuleMatcher(orgMapper),
                new PermissionSqlBuilder(new PermissionVariableResolver()));
        SysUser user = new SysUser();
        user.setId("u'1");
        when(permissionMapper.findEnabledByEntityCode("expense")).thenReturn(List.of());
        when(delegateMapper.findActiveByToUserId("u'1", "expense")).thenReturn(List.of());

        DataPermissionResult result = engine.calculatePermission("expense", user);

        assertTrue(result.isHasPermission());
        assertTrue(result.isNeedFilter());
        assertEquals("created_by = 'u''1'", result.getSqlCondition());
    }
}
