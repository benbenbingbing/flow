package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.common.PageResult;
import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.entity.SysMenu;
import com.workflow.mapper.SysMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单管理服务单元测试 - 使用Mockito
 */
@ExtendWith(MockitoExtension.class)
class SysMenuServiceTest {

    @Mock
    private SysMenuMapper menuMapper;

    @Mock
    private EntityCodeCatalogPort entityCodeCatalogPort;

    @InjectMocks
    private SysMenuService menuService;

    private SysMenu testMenu;

    @BeforeEach
    void setUp() {
        testMenu = new SysMenu();
        testMenu.setId("test-id-1");
        testMenu.setMenuName("测试菜单");
        testMenu.setMenuType("C");
        testMenu.setPath("/test/menu");
        testMenu.setComponent("test/menu/index");
        testMenu.setPerm("test:menu:list");
        testMenu.setIcon("Setting");
        testMenu.setSort(0);
        testMenu.setStatus("0");
        testMenu.setVisible("0");
        testMenu.setParentId("0");
    }

    @Test
    @DisplayName("测试查询菜单树")
    void testGetMenuTree() {
        SysMenu parentMenu = new SysMenu();
        parentMenu.setId("1");
        parentMenu.setMenuName("父菜单");
        parentMenu.setParentId("0");
        parentMenu.setSort(0);
        parentMenu.setPath("/parent");
        parentMenu.setPerm("parent:list");

        SysMenu childMenu = new SysMenu();
        childMenu.setId("2");
        childMenu.setMenuName("子菜单");
        childMenu.setParentId("1");
        childMenu.setSort(0);
        childMenu.setPath("/parent/child");
        childMenu.setPerm("parent:child:list");

        when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(parentMenu, childMenu));

        List<SysMenu> tree = menuService.getMenuTree();

        assertNotNull(tree);
        assertEquals(1, tree.size()); // 只有父菜单在顶层
        assertEquals("父菜单", tree.get(0).getMenuName());
        assertNotNull(tree.get(0).getChildren());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("子菜单", tree.get(0).getChildren().get(0).getMenuName());
    }

    @Test
    @DisplayName("测试分页查询指定父菜单下的子菜单")
    void testGetChildrenPage() {
        SysMenu childMenu = new SysMenu();
        childMenu.setId("child-id");
        childMenu.setMenuName("子菜单");
        childMenu.setParentId("parent-id");
        childMenu.setSort(1);

        Page<SysMenu> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(childMenu));
        page.setTotal(1);

        when(menuMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(menuMapper.hasChildren("child-id")).thenReturn(false);

        PageResult<SysMenu> result = menuService.getChildrenPage("parent-id", 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("子菜单", result.getRecords().get(0).getMenuName());
        assertEquals(Boolean.FALSE, result.getRecords().get(0).getHasChildren());
    }

    @Test
    @DisplayName("测试查询指定节点的完整子树")
    void testGetSubtree() {
        SysMenu childMenu = new SysMenu();
        childMenu.setId("child-id");
        childMenu.setMenuName("子菜单");
        childMenu.setParentId("parent-id");
        childMenu.setSort(1);

        SysMenu grandChildMenu = new SysMenu();
        grandChildMenu.setId("grandchild-id");
        grandChildMenu.setMenuName("孙菜单");
        grandChildMenu.setParentId("child-id");
        grandChildMenu.setSort(1);

        when(menuMapper.selectChildrenByParentId("parent-id"))
                .thenReturn(Collections.singletonList(childMenu));
        when(menuMapper.selectChildrenByParentId("child-id"))
                .thenReturn(Collections.singletonList(grandChildMenu));
        when(menuMapper.selectChildrenByParentId("grandchild-id"))
                .thenReturn(Collections.emptyList());

        List<SysMenu> subtree = menuService.getSubtree("parent-id");

        assertNotNull(subtree);
        assertEquals(1, subtree.size());
        assertEquals("子菜单", subtree.get(0).getMenuName());
        assertNotNull(subtree.get(0).getChildren());
        assertEquals(1, subtree.get(0).getChildren().size());
        assertEquals("孙菜单", subtree.get(0).getChildren().get(0).getMenuName());
    }

    @Test
    @DisplayName("测试运行态侧栏菜单过滤不存在实体的数据列表菜单")
    void testGetSidebarMenuTree_FiltersMissingEntityListMenus() {
        SysMenu homeMenu = new SysMenu();
        homeMenu.setId("100");
        homeMenu.setMenuName("首页");
        homeMenu.setParentId("0");
        homeMenu.setSort(1);
        homeMenu.setPath("/home");

        SysMenu missingByEntityCode = new SysMenu();
        missingByEntityCode.setId("missing-entity-code");
        missingByEntityCode.setMenuName("需求申请");
        missingByEntityCode.setParentId("0");
        missingByEntityCode.setSort(2);
        missingByEntityCode.setPath("/entity-list/req01/default");
        missingByEntityCode.setEntityCode("req01");

        SysMenu missingByPath = new SysMenu();
        missingByPath.setId("missing-path-code");
        missingByPath.setMenuName("立项管理");
        missingByPath.setParentId("0");
        missingByPath.setSort(3);
        missingByPath.setPath("/entity-list/project_nitiation/default");

        SysMenu validEntityMenu = new SysMenu();
        validEntityMenu.setId("valid-entity");
        validEntityMenu.setMenuName("有效实体");
        validEntityMenu.setParentId("0");
        validEntityMenu.setSort(4);
        validEntityMenu.setPath("/entity-list/customer/default");
        validEntityMenu.setEntityCode("customer");

        when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(homeMenu, missingByEntityCode, missingByPath, validEntityMenu));
        when(entityCodeCatalogPort.findAllEntityCodes()).thenReturn(java.util.Set.of("customer"));

        List<SysMenu> tree = menuService.getSidebarMenuTree();

        assertEquals(2, tree.size());
        assertEquals("首页", tree.get(0).getMenuName());
        assertEquals("有效实体", tree.get(1).getMenuName());
    }

    @Test
    @DisplayName("测试根据ID查询菜单-存在")
    void testGetById_Exists() {
        when(menuMapper.selectById("test-id-1")).thenReturn(testMenu);

        SysMenu found = menuService.getById("test-id-1");

        assertNotNull(found);
        assertEquals("测试菜单", found.getMenuName());
    }

    @Test
    @DisplayName("测试根据ID查询菜单-不存在")
    void testGetById_NotExists() {
        when(menuMapper.selectById("non-existent")).thenReturn(null);

        SysMenu found = menuService.getById("non-existent");

        assertNull(found);
    }

    @Test
    @DisplayName("测试新增菜单-成功")
    void testSaveMenu_CreateSuccess() {
        when(menuMapper.existsPerm("test:menu:list", "")).thenReturn(false);
        when(menuMapper.selectMaxSortByParentId("0")).thenReturn(5);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu newMenu = new SysMenu();
        newMenu.setMenuName("新菜单");
        newMenu.setMenuType("C");
        newMenu.setPath("/new/menu");
        newMenu.setPerm("test:menu:list");
        newMenu.setParentId("0");

        SysMenu saved = menuService.saveMenu(newMenu);

        assertNotNull(saved);
        assertEquals("新菜单", saved.getMenuName());
        assertEquals("0", saved.getStatus()); // 启用
        assertEquals("0", saved.getVisible()); // 显示
        assertEquals(6, saved.getSort()); // 5+1
        assertEquals("0", saved.getIsFrame());
        assertEquals("0", saved.getIsCache());
        verify(menuMapper).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("测试新增菜单-权限标识重复应抛出异常")
    void testSaveMenu_DuplicatePerm() {
        when(menuMapper.existsPerm("test:menu:list", "")).thenReturn(true);

        SysMenu newMenu = new SysMenu();
        newMenu.setMenuName("新菜单");
        newMenu.setMenuType("C");
        newMenu.setPath("/new/menu");
        newMenu.setPerm("test:menu:list");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            menuService.saveMenu(newMenu);
        });

        assertTrue(exception.getMessage().contains("权限标识已存在"));
        verify(menuMapper, never()).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("测试新增按钮类型菜单")
    void testSaveMenu_ButtonType() {
        when(menuMapper.existsPerm(any(), any())).thenReturn(false);
        when(menuMapper.selectMaxSortByParentId("0")).thenReturn(0);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu buttonMenu = new SysMenu();
        buttonMenu.setMenuName("测试按钮");
        buttonMenu.setMenuType("F"); // 按钮类型
        buttonMenu.setPath("/should/be/empty");
        buttonMenu.setComponent("should/be/empty");
        buttonMenu.setPerm("test:menu:add");
        buttonMenu.setParentId("0");

        SysMenu saved = menuService.saveMenu(buttonMenu);

        assertEquals("F", saved.getMenuType());
        verify(menuMapper).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("测试更新菜单-成功")
    void testSaveMenu_UpdateSuccess() {
        when(menuMapper.existsPerm("test:menu:list", "test-id-1")).thenReturn(false);
        when(menuMapper.updateById(any(SysMenu.class))).thenReturn(1);

        SysMenu updateMenu = new SysMenu();
        updateMenu.setId("test-id-1");
        updateMenu.setMenuName("更新后的菜单");
        updateMenu.setMenuType("C");
        updateMenu.setPath("/test/menu");
        updateMenu.setPerm("test:menu:list");

        SysMenu updated = menuService.saveMenu(updateMenu);

        assertEquals("test-id-1", updated.getId());
        assertEquals("更新后的菜单", updated.getMenuName());
        verify(menuMapper).updateById(any(SysMenu.class));
        verify(menuMapper, never()).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("测试删除菜单-成功")
    void testDeleteMenu_Success() {
        when(menuMapper.selectById("test-id-1")).thenReturn(testMenu);
        when(menuMapper.selectChildrenByParentId("test-id-1")).thenReturn(Collections.emptyList());
        when(menuMapper.deleteById((String) eq("test-id-1"))).thenReturn(1);

        menuService.deleteMenu("test-id-1");

        verify(menuMapper).deleteById("test-id-1");
    }

    @Test
    @DisplayName("测试删除菜单-菜单不存在应抛出异常")
    void testDeleteMenu_NotFound() {
        when(menuMapper.selectById("non-existent")).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            menuService.deleteMenu("non-existent");
        });

        assertEquals("菜单不存在", exception.getMessage());
        verify(menuMapper, never()).deleteById((String) any());
    }

    @Test
    @DisplayName("测试删除菜单-级联删除子菜单")
    void testDeleteMenu_CascadeDelete() {
        SysMenu parent = new SysMenu();
        parent.setId("parent-id");
        parent.setMenuName("父菜单");

        SysMenu child = new SysMenu();
        child.setId("child-id");
        child.setMenuName("子菜单");
        child.setParentId("parent-id");

        when(menuMapper.selectById("parent-id")).thenReturn(parent);
        when(menuMapper.selectChildrenByParentId("parent-id"))
                .thenReturn(Collections.singletonList(child));
        when(menuMapper.selectChildrenByParentId("child-id"))
                .thenReturn(Collections.emptyList());
        when(menuMapper.deleteById((String) any())).thenReturn(1);

        menuService.deleteMenu("parent-id");

        verify(menuMapper).deleteById("child-id");
        verify(menuMapper).deleteById("parent-id");
    }

    @Test
    @DisplayName("测试更新菜单状态")
    void testUpdateStatus() {
        when(menuMapper.updateById(any(SysMenu.class))).thenReturn(1);

        menuService.updateStatus("test-id-1", "1");

        verify(menuMapper).updateById(argThat((SysMenu menu) ->
            "test-id-1".equals(menu.getId()) && "1".equals(menu.getStatus())
        ));
    }

    @Test
    @DisplayName("测试更新菜单显示状态")
    void testUpdateVisible() {
        when(menuMapper.updateById(any(SysMenu.class))).thenReturn(1);

        menuService.updateVisible("test-id-1", "1");

        verify(menuMapper).updateById(argThat((SysMenu menu) ->
            "test-id-1".equals(menu.getId()) && "1".equals(menu.getVisible())
        ));
    }

    @Test
    @DisplayName("测试更新菜单排序")
    void testUpdateSort() {
        when(menuMapper.updateById(any(SysMenu.class))).thenReturn(1);

        List<String> menuIds = Arrays.asList("id-2", "id-1", "id-3");
        menuService.updateSort(menuIds);

        verify(menuMapper, times(3)).updateById(any(SysMenu.class));
    }

    @Test
    @DisplayName("测试导出菜单")
    void testExportMenus() {
        when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(testMenu));

        List<SysMenu> menus = menuService.exportMenus();

        assertNotNull(menus);
        assertEquals(1, menus.size());
        assertEquals("测试菜单", menus.get(0).getMenuName());
    }

    @Test
    @DisplayName("测试获取菜单类型选项")
    void testGetMenuTypeOptions() {
        var options = menuService.getMenuTypeOptions();

        assertEquals(3, options.size());
        assertEquals("M", options.get(0).get("value"));
        assertEquals("目录", options.get(0).get("label"));
        assertEquals("C", options.get(1).get("value"));
        assertEquals("菜单", options.get(1).get("label"));
        assertEquals("F", options.get(2).get("value"));
        assertEquals("按钮", options.get(2).get("label"));
    }

    @Test
    @DisplayName("测试导入菜单-成功")
    void testImportMenus() {
        when(menuMapper.existsPerm("import:menu:list", "")).thenReturn(false);
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

        SysMenu importMenu = new SysMenu();
        importMenu.setMenuName("导入菜单");
        importMenu.setMenuType("C");
        importMenu.setPath("/import/menu");
        importMenu.setPerm("import:menu:list");

        menuService.importMenus(Collections.singletonList(importMenu));

        verify(menuMapper).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("测试导入菜单-跳过已存在的权限标识")
    void testImportMenus_SkipDuplicatePerm() {
        when(menuMapper.existsPerm("import:menu:list", "")).thenReturn(true);

        SysMenu importMenu = new SysMenu();
        importMenu.setMenuName("导入菜单");
        importMenu.setMenuType("C");
        importMenu.setPath("/import/menu");
        importMenu.setPerm("import:menu:list");

        // 应该正常执行，不会抛出异常
        assertDoesNotThrow(() -> menuService.importMenus(Collections.singletonList(importMenu)));
        
        // 不应该插入
        verify(menuMapper, never()).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("测试新增子菜单")
    void testSaveChildMenu() {
        when(menuMapper.existsPerm(any(), any())).thenReturn(false);
        when(menuMapper.selectMaxSortByParentId("parent-id")).thenReturn(0);
        when(menuMapper.insert(any(SysMenu.class))).thenAnswer(invocation -> {
            SysMenu menu = invocation.getArgument(0);
            menu.setId("child-generated-id");
            return 1;
        });

        SysMenu childMenu = new SysMenu();
        childMenu.setMenuName("子菜单");
        childMenu.setMenuType("C");
        childMenu.setPath("/parent/child");
        childMenu.setPerm("parent:child:list");
        childMenu.setParentId("parent-id");

        SysMenu saved = menuService.saveMenu(childMenu);

        assertNotNull(saved.getId());
        assertEquals("parent-id", saved.getParentId());
    }
}
