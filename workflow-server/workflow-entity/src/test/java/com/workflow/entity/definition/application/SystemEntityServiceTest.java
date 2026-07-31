package com.workflow.entity.definition.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.security.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemEntityServiceTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysGroupMapper groupMapper = mock(SysGroupMapper.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysDictMapper dictMapper = mock(SysDictMapper.class);
    private final SysDictItemMapper dictItemMapper =
            mock(SysDictItemMapper.class);
    private final SystemEntityService service = new SystemEntityService(
            userMapper,
            organizationMapper,
            roleMapper,
            groupMapper,
            menuMapper,
            dictMapper,
            dictItemMapper);

    @BeforeEach
    void setUpPermissionContext() {
        new PermissionUtil(
                mock(SysUserRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                menuMapper).init();
        UserContext.setCurrentUser("test-user", "tester");
        when(menuMapper.selectPermsByUserId("test-user"))
                .thenReturn(Set.of("*"));
    }

    @AfterEach
    void clearPermissionContext() {
        UserContext.clear();
    }

    @Test
    void selectsUsersByUsernameAndKeepsRequestedOrder() {
        SysUser admin = user("user-1", "admin", "管理员");
        SysUser reviewer = user("user-2", "reviewer", "审批人");
        when(userMapper.selectList(any())).thenReturn(
                List.of(reviewer, admin));

        List<Map<String, Object>> result = service.selectBatch(
                "USER",
                List.of("admin", "reviewer"),
                "code");

        assertEquals(
                List.of("admin", "reviewer"),
                result.stream().map(item -> item.get("code")).toList());
        assertEquals(
                List.of("管理员", "审批人"),
                result.stream().map(item -> item.get("name")).toList());
    }

    private SysUser user(String id, String username, String nickname) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setStatus("0");
        return user;
    }
}
