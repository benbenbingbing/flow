package com.workflow.service;

import com.workflow.common.BusinessConflictException;
import com.workflow.common.ForbiddenException;
import com.workflow.common.UserContext;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.SysRole;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.SysRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiConfigurationAccessServiceTest {

    private SysRoleMapper roleMapper;
    private EntityDefinitionMapper definitionMapper;
    private EntityFormMapper formMapper;
    private EntityListConfigMapper listConfigMapper;
    private UiConfigurationAccessService accessService;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        formMapper = mock(EntityFormMapper.class);
        listConfigMapper = mock(EntityListConfigMapper.class);
        accessService = new UiConfigurationAccessService(
                new CurrentUserRoleService(roleMapper),
                new EntityDefinitionAccessPolicy(definitionMapper),
                formMapper,
                listConfigMapper);
        UserContext.setCurrentUser("user-1", "tester");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void ordinaryUserCannotWriteGlobalConfiguration() {
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(role("member")));

        assertThrows(
                ForbiddenException.class,
                accessService::requireGlobalConfigurationAccess);
    }

    @Test
    void ordinaryUserIsRejectedBeforeFormOrListLookup() {
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(role("member")));

        assertThrows(
                ForbiddenException.class,
                () -> accessService.requireFormAccess("form-1"));
        assertThrows(
                ForbiddenException.class,
                () -> accessService.requireListAccess("list-1"));

        verify(formMapper, never()).selectById("form-1");
        verify(listConfigMapper, never()).selectById("list-1");
    }

    @Test
    void administratorCanWriteGlobalAndDynamicEntityConfiguration() {
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(role("admin")));
        EntityDefinition definition = dynamicEntity("entity-1");
        EntityForm form = new EntityForm();
        form.setEntityId("entity-1");
        EntityListConfig list = new EntityListConfig();
        list.setEntityId("entity-1");
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(listConfigMapper.selectById("list-1")).thenReturn(list);
        when(definitionMapper.selectById("entity-1")).thenReturn(definition);

        assertDoesNotThrow(accessService::requireGlobalConfigurationAccess);
        assertDoesNotThrow(() -> accessService.requireFormAccess("form-1"));
        assertDoesNotThrow(() -> accessService.requireListAccess("list-1"));
    }

    @Test
    void missingFormOrListIsRejectedForAdministrator() {
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(role("admin")));

        IllegalArgumentException formError = assertThrows(
                IllegalArgumentException.class,
                () -> accessService.requireFormAccess("missing-form"));
        IllegalArgumentException listError = assertThrows(
                IllegalArgumentException.class,
                () -> accessService.requireListAccess("missing-list"));

        assertEquals("表单不存在: missing-form", formError.getMessage());
        assertEquals("列表配置不存在: missing-list", listError.getMessage());
    }

    @Test
    void systemEntityFormIsRejectedForAdministrator() {
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(role("admin")));
        EntityForm form = new EntityForm();
        form.setEntityId("system-entity");
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(definitionMapper.selectById("system-entity"))
                .thenReturn(systemEntity("system-entity"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> accessService.requireFormAccess("form-1"));

        assertEquals("ENTITY_SYSTEM_DEFINITION_PROTECTED", exception.getErrorCode());
    }

    @Test
    void systemEntityListIsRejectedForAdministrator() {
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(role("super_admin")));
        EntityListConfig list = new EntityListConfig();
        list.setEntityId("system-entity");
        when(listConfigMapper.selectById("list-1")).thenReturn(list);
        when(definitionMapper.selectById("system-entity"))
                .thenReturn(systemEntity("system-entity"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> accessService.requireListAccess("list-1"));

        assertEquals("ENTITY_SYSTEM_DEFINITION_PROTECTED", exception.getErrorCode());
    }

    private SysRole role(String roleCode) {
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        return role;
    }

    private EntityDefinition dynamicEntity(String id) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id);
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        return entity;
    }

    private EntityDefinition systemEntity(String id) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id);
        entity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        return entity;
    }
}
