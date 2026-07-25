package com.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.common.PageResult;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysOrganizationMapper orgMapper;

    @InjectMocks
    private SysUserService userService;

    @Test
    void getUsersByRolePageNormalizesPagingAndEnrichesUsers() {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("alice");

        Page<SysUser> page = new Page<>(1, 100);
        page.setRecords(Collections.singletonList(user));
        page.setTotal(1);

        SysRole role = new SysRole();
        role.setId("role-1");
        role.setRoleName("审批员");

        when(userMapper.selectPageByRoleId(
                argThat(value -> value.getCurrent() == 1 && value.getSize() == 100),
                eq("role-1"),
                eq("alice")))
                .thenReturn(page);
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(Collections.singletonList(role));

        PageResult<SysUser> result = userService.getUsersByRolePage(
                "role-1",
                0,
                200,
                " alice ");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(100, result.getPageSize());
        assertEquals("user-1", result.getRecords().get(0).getId());
        assertEquals(Collections.singletonList("role-1"), result.getRecords().get(0).getRoleIds());
        verify(roleMapper).selectRolesByUserId("user-1");
    }
}
