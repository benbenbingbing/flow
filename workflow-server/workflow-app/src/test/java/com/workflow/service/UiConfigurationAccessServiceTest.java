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

/**
 * UI 配置访问控制服务测试。
 *
 * <p>被测对象：{@link UiConfigurationAccessService}，覆盖普通用户与管理员对全局配置、表单、列表配置的写权限校验，
 * 以及系统实体受保护、缺失资源拒绝等场景。
 */
class UiConfigurationAccessServiceTest {

    private SysRoleMapper roleMapper;
    private EntityDefinitionMapper definitionMapper;
    private EntityFormMapper formMapper;
    private EntityListConfigMapper listConfigMapper;
    /** 被测访问控制服务 */
    private UiConfigurationAccessService accessService;

    /** 装配被测服务及其真实依赖组件，并设置当前用户上下文 */
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

    /** 清理用户上下文，避免用例间污染 */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 测试普通用户不能写全局配置：验证抛出 ForbiddenException */
    @Test
    void ordinaryUserCannotWriteGlobalConfiguration() {
        when(roleMapper.selectRolesByUserId("user-1"))
                .thenReturn(List.of(role("member")));

        assertThrows(
                ForbiddenException.class,
                accessService::requireGlobalConfigurationAccess);
    }

    /** 测试普通用户在查询表单/列表前即被拒绝：验证权限不足时不触发表单与列表查询 */
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

    /** 测试管理员可写全局配置与动态实体配置：验证全局、表单、列表访问校验均通过 */
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

    /** 测试表单/列表不存在时对管理员拒绝：验证抛出 IllegalArgumentException 且消息包含资源标识 */
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

    /** 测试系统实体表单对管理员拒绝：验证抛出业务冲突异常且错误码为 ENTITY_SYSTEM_DEFINITION_PROTECTED */
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

    /** 测试系统实体列表对超级管理员拒绝：验证抛出业务冲突异常且错误码为 ENTITY_SYSTEM_DEFINITION_PROTECTED */
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

    /** 构造指定角色编码的角色对象 */
    private SysRole role(String roleCode) {
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        return role;
    }

    /** 构造动态存储模式的实体定义 */
    private EntityDefinition dynamicEntity(String id) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id);
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        return entity;
    }

    /** 构造系统存储模式的实体定义（受保护） */
    private EntityDefinition systemEntity(String id) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id);
        entity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        return entity;
    }
}
