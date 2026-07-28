package com.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.core.result.PageResult;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void createUserHashesSuppliedPasswordAndResponseCannotSerializeIt() throws Exception {
        SysUser user = new SysUser();
        user.setUsername("alice");
        user.setPassword("InitialPass9");

        SysUser saved = userService.saveUser(user);

        verify(userMapper).insert(argThat((SysUser inserted) ->
                Boolean.TRUE.equals(inserted.getPasswordResetRequired())
                        && userService.passwordMatches("InitialPass9", inserted.getPassword())));
        assertFalse(new ObjectMapper()
                .findAndRegisterModules()
                .valueToTree(saved)
                .has("password"));
    }

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
    void resetPasswordStoresSuppliedPasswordWithoutReturningIt() {
        SysUser existing = new SysUser();
        existing.setId("user-1");
        when(userMapper.selectById("user-1")).thenReturn(existing);
        when(userMapper.incrementTokenVersion("user-1")).thenReturn(1);

        userService.resetPassword("user-1", "TemporaryPass9");

        verify(userMapper).updateById(argThat((SysUser user) ->
                Boolean.TRUE.equals(user.getPasswordResetRequired())
                        && userService.passwordMatches("TemporaryPass9", user.getPassword())));
    }

    @Test
    void resetPasswordRejectsWeakValues() {
        SysUser existing = new SysUser();
        existing.setId("user-1");
        when(userMapper.selectById("user-1")).thenReturn(existing);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.resetPassword("user-1", "123456"));
    }

    @Test
    void changePasswordClearsRequiredFlag() {
        SysUser existing = new SysUser();
        existing.setId("user-1");
        String currentPassword = "CurrentPass1";
        existing.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(currentPassword));
        when(userMapper.selectById("user-1")).thenReturn(existing);
        when(userMapper.incrementTokenVersion("user-1")).thenReturn(1);

        userService.changePassword("user-1", currentPassword, "NextPassword2");

        verify(userMapper).updateById(argThat((SysUser user) ->
                Boolean.FALSE.equals(user.getPasswordResetRequired())
                        && userService.passwordMatches("NextPassword2", user.getPassword())));
        assertFalse(userService.passwordMatches("NextPassword2", existing.getPassword()));
    }

}
