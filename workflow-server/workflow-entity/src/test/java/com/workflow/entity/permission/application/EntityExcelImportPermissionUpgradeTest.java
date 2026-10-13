package com.workflow.entity.permission.application;

import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRoleMenu;
import com.workflow.contracts.bootstrap.port.BootstrapJobPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.permission.bootstrap.EntityPermissionBootstrapRunner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 模拟旧目录任务已完成的升级，验证实际菜单及角色关联被补齐且原按钮配置未被重新初始化。 */
class EntityExcelImportPermissionUpgradeTest {
    private final EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
    private final EntityListConfigMapper lists = mock(EntityListConfigMapper.class);
    private final EntityStatusMapper statuses = mock(EntityStatusMapper.class);
    private final SysMenuMapper menus = mock(SysMenuMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenus = mock(SysRoleMenuMapper.class);
    private final EntityListActionConfigService buttons = mock(EntityListActionConfigService.class);
    private final EntityPermissionCatalogService catalog = spy(new EntityPermissionCatalogService(
            definitions, lists, statuses, menus, roles, roleMenus, buttons, List.of()));
    private final Map<String, SysMenu> stored = new HashMap<>();
    private final Set<String> grants = new HashSet<>();

    @Test
    void completedOldBootstrapDoesNotPreventImportResourceUpgradeAndPreservesButtons() {
        prepareCatalog();
        var bootstrap = new CompletedJobs(Set.of("entity-permission-catalog:1", "entity-restart-process-permission:1"));
        var runner = new EntityPermissionBootstrapRunner(catalog, bootstrap);
        runner.run(null);
        runner.run(null);

        assertThat(stored.keySet()).containsExactlyInAnyOrder("entity:employee:import", "entity:project:import");
        assertThat(stored.values()).allSatisfy(menu -> {
            assertThat(menu.getMenuType()).isEqualTo("F");
            assertThat(menu.getStatus()).isEqualTo("0");
            assertThat(grants).contains("admin:" + menu.getId(), "super:" + menu.getId());
        });
        assertThat(grants).hasSize(4);
        assertThat(bootstrap.completed).contains("entity-excel-import-permission:1");
        verify(catalog, times(1)).synchronizeImportPermissions();
        verify(catalog, never()).synchronizeAll();
        verify(catalog, never()).synchronizeRestartPermissions();
        verifyNoInteractions(lists, statuses, buttons);
    }

    @Test
    void retryReusesExistingPermissionAndRoleBindings() {
        prepareCatalog();
        catalog.synchronizeImportPermissions();
        catalog.synchronizeImportPermissions();
        assertThat(stored).hasSize(2);
        assertThat(grants).hasSize(4);
        verify(menus, times(2)).insert(any(SysMenu.class));
        verify(roleMenus, times(4)).insert(any(SysRoleMenu.class));
        verifyNoInteractions(lists, statuses, buttons);
    }

    @Test
    void systemOnlyInstallationDoesNotCreateGenericImportPermissions() {
        when(definitions.selectList(any())).thenReturn(List.of(entity("sys_user", EntityDefinition.StorageMode.SYSTEM, EntityDefinition.LifecycleMode.STANDALONE)));
        catalog.synchronizeImportPermissions();
        verifyNoInteractions(menus, roles, roleMenus, lists, statuses, buttons);
    }

    private void prepareCatalog() {
        when(definitions.selectList(any())).thenReturn(List.of(
                entity("employee", EntityDefinition.StorageMode.DYNAMIC, EntityDefinition.LifecycleMode.STANDALONE),
                entity("project", EntityDefinition.StorageMode.DYNAMIC, EntityDefinition.LifecycleMode.WORKFLOW),
                entity("sys_user", EntityDefinition.StorageMode.SYSTEM, EntityDefinition.LifecycleMode.STANDALONE)));
        when(roles.selectAdministratorRoles()).thenReturn(List.of(role("admin", "admin"), role("super", "super_admin")));
        when(menus.selectByPathAndType(anyString(), anyString())).thenAnswer(call -> {
            SysMenu menu = new SysMenu(); menu.setId(call.getArgument(0)); return menu;
        });
        when(menus.selectByPerm(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        doAnswer(call -> {
            SysMenu menu = call.getArgument(0); menu.setId("menu-" + stored.size()); stored.put(menu.getPerm(), menu); return 1;
        }).when(menus).insert(any(SysMenu.class));
        when(roleMenus.existsRoleMenu(anyString(), anyString())).thenAnswer(call -> grants.contains(call.getArgument(0) + ":" + call.getArgument(1)));
        doAnswer(call -> {
            SysRoleMenu binding = call.getArgument(0); grants.add(binding.getRoleId() + ":" + binding.getMenuId()); return 1;
        }).when(roleMenus).insert(any(SysRoleMenu.class));
    }

    private static EntityDefinition entity(String code, EntityDefinition.StorageMode storage, EntityDefinition.LifecycleMode lifecycle) {
        EntityDefinition entity = new EntityDefinition(); entity.setId(code); entity.setEntityCode(code); entity.setEntityName(code);
        entity.setStorageMode(storage); entity.setLifecycleMode(lifecycle); return entity;
    }

    private static SysRole role(String id, String code) {
        SysRole role = new SysRole(); role.setId(id); role.setRoleCode(code); return role;
    }

    private static final class CompletedJobs implements BootstrapJobPort {
        private final Set<String> completed;
        private CompletedJobs(Set<String> completed) { this.completed = new HashSet<>(completed); }
        public <T> Optional<T> executeOnce(String name, int version, Supplier<T> action) {
            String key = name + ":" + version;
            if (completed.contains(key)) return Optional.empty();
            T result = action.get(); completed.add(key); return Optional.ofNullable(result);
        }
    }
}
