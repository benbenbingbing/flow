package com.workflow.controller;

import com.workflow.core.result.PageResult;
import com.workflow.admin.authorization.role.api.web.SysRoleController;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.authorization.menu.application.SysMenuService;
import com.workflow.admin.authorization.role.application.SysRoleService;
import com.workflow.admin.identity.user.application.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SysRoleController.class)
@ActiveProfiles("test")
class SysRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SysRoleService roleService;

    @MockitoBean
    private SysMenuService menuService;

    @MockitoBean
    private SysUserService userService;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("user-1", "admin");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void getRoleUsersReturnsPagedMembers() throws Exception {
        SysUser user = new SysUser();
        user.setId("member-1");
        user.setUsername("alice");
        user.setNickname("Alice");

        PageResult<SysUser> result = new PageResult<>(
                Collections.singletonList(user),
                1,
                1,
                10);
        when(userService.getUsersByRolePage("role-1", 1, 10, "alice"))
                .thenReturn(result);

        mockMvc.perform(get("/api/system/role/{id}/users", "role-1")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .param("keyword", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].username").value("alice"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10));

        verify(userService).getUsersByRolePage("role-1", 1, 10, "alice");
    }
}
