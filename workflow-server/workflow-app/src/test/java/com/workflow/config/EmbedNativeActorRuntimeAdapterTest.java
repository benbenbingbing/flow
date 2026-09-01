package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EmbedNativeActorRuntimeAdapterTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void returnsRealtimeMappedUserRolesAndPermissionsForNativeUi() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("alice");
        user.setNickname("Alice Zhang");
        SysRole admin = new SysRole();
        admin.setRoleCode("super_admin");
        when(userMapper.selectById("user-1")).thenReturn(user);
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(admin));
        when(menuMapper.selectPermsByUserId("user-1"))
                .thenReturn(Set.of("entity:order:view", "storage:file:upload"));
        UserContext.setCurrentUser("user-1", "alice", "embed-session-1");
        EmbedNativeActorRuntimeAdapter adapter =
                new EmbedNativeActorRuntimeAdapter(
                        userMapper, roleMapper, menuMapper);

        var actor = adapter.resolve("user-1", "alice", "合作方用户");

        assertEquals("alice", actor.username());
        assertEquals("Alice Zhang", actor.nickname());
        assertEquals("合作方用户", actor.displayName());
        assertEquals(List.of("super_admin"), actor.roles());
        assertTrue(actor.isSuperAdmin());
        assertEquals(
                List.of("entity:order:view", "storage:file:upload"),
                actor.permissions());
    }
}
