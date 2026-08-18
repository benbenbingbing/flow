package com.workflow.entity.data.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemEntityReadServiceTest {

    private JdbcTemplate jdbcTemplate;
    private EntityDefinitionMapper definitionMapper;
    private EntityFieldMapper fieldMapper;
    private SystemEntityReadService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        fieldMapper = mock(EntityFieldMapper.class);
        service = new SystemEntityReadService(
                jdbcTemplate,
                definitionMapper,
                fieldMapper,
                new SystemEntityFieldPolicy());

        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId("user-1"))
                .thenReturn(Set.of("system:user:view"));
        new PermissionUtil(
                mock(SysUserRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                menuMapper).init();
        UserContext.setCurrentUser("user-1", "reader");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void queryUsesCatalogWhitelistAndNeverSelectsSensitiveColumns() {
        EntityDefinition entity = systemEntity("sys_user");
        when(definitionMapper.findByEntityCode("sys_user"))
                .thenReturn(Optional.of(entity));
        when(fieldMapper.findByEntityId("entity-1"))
                .thenReturn(List.of(
                        field("id"),
                        field("username"),
                        field("password"),
                        field("token_version"),
                        field("deleted"),
                        field("create_time")));
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", "1",
                        "username", "admin",
                        "deleted", 0)));

        PageResult<EntityDataDTO> result =
                service.findPage(
                        "sys_user",
                        Map.of(
                                "username", "adm",
                                "username_op", "LIKE",
                                "deleted", 1),
                        1,
                        500,
                        "username",
                        "DESC");

        assertEquals(200, result.getPageSize());
        assertEquals("admin", result.getRecords().get(0).getName());
        assertFalse(result.getRecords().get(0).getData()
                .containsKey("password"));
        assertFalse(result.getRecords().get(0).getData()
                .containsKey("token_version"));

        ArgumentCaptor<String> countSql =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                countSql.capture(),
                eq(Long.class),
                any(Object[].class));
        ArgumentCaptor<String> pageSql =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(
                pageSql.capture(),
                any(Object[].class));

        assertTrue(countSql.getValue()
                .contains("`deleted` = 0"));
        assertTrue(pageSql.getValue()
                .contains("ORDER BY `username` DESC"));
        assertFalse(pageSql.getValue()
                .contains("password"));
        assertFalse(pageSql.getValue()
                .contains("token_version"));
    }

    @Test
    void unknownFieldsOperatorsAndSortsAreRejected() {
        EntityDefinition entity = systemEntity("sys_user");
        when(definitionMapper.findByEntityCode("sys_user"))
                .thenReturn(Optional.of(entity));
        when(fieldMapper.findByEntityId("entity-1"))
                .thenReturn(List.of(
                        field("id"),
                        field("username"),
                        field("deleted")));
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(Object[].class)))
                .thenReturn(0L);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findPage(
                        "sys_user",
                        Map.of("password", "secret"),
                        1,
                        20));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findPage(
                        "sys_user",
                        Map.of(
                                "username", "admin",
                                "username_op", "RAW_SQL"),
                        1,
                        20));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findPage(
                        "sys_user",
                        Map.of(),
                        1,
                        20,
                        "password",
                        "ASC"));
    }

    @Test
    void selectorSearchesSystemRecordsByDisplayAndCodeFields() {
        EntityDefinition entity = systemEntity("sys_user");
        when(definitionMapper.findByEntityCode("sys_user"))
                .thenReturn(Optional.of(entity));
        when(fieldMapper.findByEntityId("entity-1"))
                .thenReturn(List.of(
                        field("id"),
                        field("username"),
                        field("nickname"),
                        field("password"),
                        field("deleted")));
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", "1",
                        "username", "admin",
                        "nickname", "超级管理员",
                        "deleted", 0)));

        PageResult<EntityDataDTO> result =
                service.findSelectorPage(
                        "sys_user", "管理", 1, 20);

        assertEquals("超级管理员",
                result.getRecords().get(0).getName());
        ArgumentCaptor<String> countSql =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                countSql.capture(),
                eq(Long.class),
                any(Object[].class));
        assertTrue(countSql.getValue()
                .contains("`deleted` = 0"));
        assertTrue(countSql.getValue()
                .contains("`nickname` LIKE ?"));
        assertTrue(countSql.getValue()
                .contains("`username` LIKE ?"));
        assertFalse(countSql.getValue()
                .contains("password"));
    }

    @Test
    void identitySelectorDoesNotRequireOrganizationAdminPermission() {
        grantPermissions("entity:ZDWREQ:list");
        EntityDefinition entity = systemEntity("sys_organization");
        when(definitionMapper.findByEntityCode("sys_organization"))
                .thenReturn(Optional.of(entity));
        when(fieldMapper.findByEntityId("entity-1"))
                .thenReturn(List.of(field("id"), field("org_name"), field("deleted")));
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(Object[].class)))
                .thenReturn(0L);

        PageResult<EntityDataDTO> result =
                service.findSelectorPage("sys_organization", "研发", 1, 10);

        assertEquals(0, result.getTotal());
    }

    @Test
    void menuTableStillRequiresMenuAdminPermission() {
        grantPermissions("entity:ZDWREQ:list");

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.requirePermissions("sys_menu"));
        assertEquals(
                "没有权限访问平台系统表：system:menu:view",
                error.getMessage());
    }

    private void grantPermissions(String... permissions) {
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId("user-1"))
                .thenReturn(Set.of(permissions));
        new PermissionUtil(
                mock(SysUserRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                menuMapper).init();
    }

    private EntityDefinition systemEntity(String entityCode) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode(entityCode);
        entity.setEntityName("系统用户");
        entity.setPhysicalTableName(entityCode);
        entity.setStorageMode(
                EntityDefinition.StorageMode.SYSTEM);
        return entity;
    }

    private EntityField field(String fieldCode) {
        EntityField field = new EntityField();
        field.setFieldCode(fieldCode);
        field.setDbColumnName(fieldCode);
        return field;
    }
}
