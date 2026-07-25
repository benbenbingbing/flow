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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void resetPasswordGeneratesOneTimePasswordAndRequiresChange() {
        SysUser existing = new SysUser();
        existing.setId("user-1");
        when(userMapper.selectById("user-1")).thenReturn(existing);

        String temporaryPassword = userService.resetPassword("user-1");

        assertTrue(temporaryPassword.length() >= 10);
        verify(userMapper).updateById(argThat((SysUser user) ->
                Boolean.TRUE.equals(user.getPasswordResetRequired())
                        && userService.passwordMatches(temporaryPassword, user.getPassword())));
    }

    @Test
    void generatedTemporaryPasswordsAreNotFixedDefaults() {
        SysUser first = new SysUser();
        first.setId("user-1");
        SysUser second = new SysUser();
        second.setId("user-2");
        when(userMapper.selectById("user-1")).thenReturn(first);
        when(userMapper.selectById("user-2")).thenReturn(second);

        String firstPassword = userService.resetPassword("user-1");
        String secondPassword = userService.resetPassword("user-2");

        assertNotEquals("123456", firstPassword);
        assertNotEquals(firstPassword, secondPassword);
    }

    @Test
    void changePasswordClearsRequiredFlag() {
        SysUser existing = new SysUser();
        existing.setId("user-1");
        String currentPassword = "CurrentPass1";
        existing.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(currentPassword));
        when(userMapper.selectById("user-1")).thenReturn(existing);

        userService.changePassword("user-1", currentPassword, "NextPassword2");

        verify(userMapper).updateById(argThat((SysUser user) ->
                Boolean.FALSE.equals(user.getPasswordResetRequired())
                        && userService.passwordMatches("NextPassword2", user.getPassword())));
        assertFalse(userService.passwordMatches("NextPassword2", existing.getPassword()));
    }

}
