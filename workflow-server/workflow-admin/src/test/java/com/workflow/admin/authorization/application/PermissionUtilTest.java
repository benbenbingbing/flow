package com.workflow.admin.authorization.application;

import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.security.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionUtilTest {

    private final SysMenuMapper menuMapper =
            mock(SysMenuMapper.class);

    @BeforeEach
    void setUp() {
        new PermissionUtil(
                mock(SysUserRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                menuMapper).init();
        UserContext.setCurrentUser("super-admin", "admin");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void wildcardGrantsSingleAndAnyPermissionChecks() {
        when(menuMapper.selectPermsByUserId("super-admin"))
                .thenReturn(Set.of("*"));

        assertTrue(PermissionUtil.hasPermission(
                "system:organization:view"));
        assertTrue(PermissionUtil.hasAnyPermission(List.of(
                "system:user:view",
                "system:organization:view")));
    }
}
