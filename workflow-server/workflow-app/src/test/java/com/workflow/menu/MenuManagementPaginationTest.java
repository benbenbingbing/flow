package com.workflow.menu;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.admin.authorization.menu.application.SysMenuService;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 真实数据库分页验证菜单浏览、搜索父链和单行排序，避免树形界面依赖全量目录。 */
class MenuManagementPaginationTest {
    private JdbcTemplate jdbc;
    private SysMenuService service;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:menu_page_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE sys_menu (
                  id VARCHAR(64) PRIMARY KEY, parent_id VARCHAR(64), menu_name VARCHAR(100),
                  menu_type CHAR(1), icon VARCHAR(100), sort INT DEFAULT 0, path VARCHAR(200),
                  component VARCHAR(255), perm VARCHAR(200), status CHAR(1) DEFAULT '0',
                  visible CHAR(1) DEFAULT '0', is_frame CHAR(1), is_cache CHAR(1), query VARCHAR(255),
                  entity_code VARCHAR(100), resource_type VARCHAR(30), list_key VARCHAR(100),
                  create_time TIMESTAMP DEFAULT '2026-01-01 00:00:00', update_time TIMESTAMP,
                  deleted INT DEFAULT 0)
                """);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SysMenuMapper.class);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(pagination);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(SysMenuMapper.class);
        service = new SysMenuService(mapper, mock(SysRoleMapper.class), mock(EntityCodeCatalogPort.class));
        insert("system", "0", "M", "系统管理");
        insert("other", "0", "M", "其他目录");
        insert("legacy", null, "M", "历史顶级菜单");
        for (int i = 0; i < 55; i++) insert("page%02d".formatted(i), "system", "C", "页面%02d".formatted(i));
        insert("nested", "page54", "C", "深层菜单");
        insert("button", "nested", "F", "新增按钮");
        jdbc.update("UPDATE sys_menu SET path='/deep/entry', perm='deep:entry:add' WHERE id='nested'");
    }

    @AfterEach
    void cleanup() {
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void directChildrenArePagedWithStableOrderAndDisabledHiddenRowsRemainManageable() {
        jdbc.update("UPDATE sys_menu SET status='1', visible='1' WHERE id='page00'");
        var first = service.getManagementPage("system", 1, 50, null, null, null, false);
        var second = service.getManagementPage("system", 2, 50, null, null, null, false);
        assertEquals(55, first.getTotal());
        assertEquals(50, first.getRecords().size());
        assertEquals(5, second.getRecords().size());
        assertEquals("page00", first.getRecords().get(0).getId());
        assertEquals("1", first.getRecords().get(0).getStatus());
        assertEquals("1", first.getRecords().get(0).getVisible());
        var last = second.getRecords().get(4);
        assertEquals("page54", last.getId());
        assertEquals("系统管理 / 页面54", last.getDisplayPath());
        assertTrue(last.getHasChildren());
        assertFalse(first.getRecords().get(0).getHasChildren());
    }

    @Test
    void globalSearchMatchesNamesRoutesAndPermissionsFromAnyCurrentScope() {
        for (String keyword : List.of("深层", "/deep/entry", "deep:entry:add")) {
            var page = service.getManagementPage("other", 1, 20, keyword, null, null, false);
            assertEquals(List.of("nested"), ids(page.getRecords()));
            assertEquals("系统管理 / 页面54 / 深层菜单", page.getRecords().get(0).getDisplayPath());
        }
        assertEquals(List.of("button"), ids(service.getManagementPage("0", 1, 20, "新增", "F", null, false).getRecords()));
        assertEquals(0, service.getManagementPage("0", 1, 20, "新增", "M", null, false).getTotal());
    }

    @Test
    void parentPickerExcludesSelfAndButtonsAndDisablesDescendantSearchResults() {
        var browse = service.getManagementPage("system", 1, 100, null, null, "page54", true);
        assertEquals(54, browse.getTotal());
        assertFalse(ids(browse.getRecords()).contains("page54"));
        var descendants = service.getManagementPage("0", 1, 20, "深层", null, "page54", true);
        assertEquals(1, descendants.getTotal());
        assertTrue(descendants.getRecords().get(0).isSelectionDisabled());
        assertEquals(0, service.getManagementPage("0", 1, 20, "新增", null, "page54", true).getTotal());
        assertEquals(0, service.getManagementPage("nested", 1, 20, null, null, "page54", true).getTotal());
    }

    @Test
    void nullParentRootsAndOrderedAncestorsAreSupportedAndDeletedNodesAreExcluded() {
        assertEquals(3, service.getManagementPage("0", 1, 20, null, null, null, false).getTotal());
        var ancestors = service.getAncestors("button");
        assertEquals(List.of("system", "page54", "nested", "button"), ids(ancestors));
        assertEquals("系统管理 / 页面54 / 深层菜单 / 新增按钮", ancestors.get(3).getDisplayPath());
        assertEquals(List.of("legacy"), ids(service.getAncestors("legacy")));
        jdbc.update("UPDATE sys_menu SET deleted=1 WHERE id='button'");
        assertFalse(service.getManagementPage("page54", 1, 20, null, null, null, false).getRecords().get(0).getHasChildren());
        assertThrows(IllegalArgumentException.class, () -> service.getAncestors("button"));
    }

    @Test
    void brokenAndCyclicParentsCannotHangNavigationAndRemainVisibleForRepair() {
        insert("orphan", "missing", "C", "待修复菜单");
        insert("cycle1", "cycle2", "M", "循环一");
        insert("cycle2", "cycle1", "M", "循环二");
        assertThrows(IllegalArgumentException.class, () -> service.getAncestors("orphan"));
        assertThrows(IllegalArgumentException.class, () -> service.getAncestors("cycle1"));
        var result = service.getManagementPage("0", 1, 20, "待修复", null, null, false);
        assertEquals("父链异常 / 待修复菜单", result.getRecords().get(0).getDisplayPath());
        assertTrue(result.getRecords().get(0).isSelectionDisabled());
    }

    @Test
    void rowSortUpdatesOnlySortAndDoesNotReparentOrOverwriteMetadata() {
        service.updateSortValue("nested", 8);
        assertEquals(8, jdbc.queryForObject("SELECT sort FROM sys_menu WHERE id='nested'", Integer.class));
        assertEquals("page54", jdbc.queryForObject("SELECT parent_id FROM sys_menu WHERE id='nested'", String.class));
        assertEquals("/deep/entry", jdbc.queryForObject("SELECT path FROM sys_menu WHERE id='nested'", String.class));
        assertEquals("deep:entry:add", jdbc.queryForObject("SELECT perm FROM sys_menu WHERE id='nested'", String.class));
        assertThrows(IllegalArgumentException.class, () -> service.updateSortValue("nested", null));
        assertThrows(IllegalArgumentException.class, () -> service.updateSortValue("nested", -1));
        assertThrows(IllegalArgumentException.class, () -> service.updateSortValue("nested", 10000));
        assertThrows(IllegalArgumentException.class, () -> service.updateSortValue("missing", 1));
    }

    @Test
    void invalidPageTypeAndScopeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage("0", 0, 20, null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage("0", 1, 101, null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage("0", 1, 0, null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage("0", 1, 20, null, "BAD", null, false));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage("missing", 1, 20, null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage("0", 1, 20, null, null, "missing", true));
    }

    private void insert(String id, String parentId, String type, String name) {
        jdbc.update("INSERT INTO sys_menu(id,parent_id,menu_type,menu_name) VALUES(?,?,?,?)", id, parentId, type, name);
    }

    private List<String> ids(List<SysMenu> rows) {
        return rows.stream().map(SysMenu::getId).toList();
    }
}
