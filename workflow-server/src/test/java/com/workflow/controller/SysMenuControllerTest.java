package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.SysMenu;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.service.SysMenuService;
import com.workflow.service.permission.EntityPermissionCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 菜单管理控制器单元测试
 */
@WebMvcTest(SysMenuController.class)
@ActiveProfiles("test")
class SysMenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SysMenuService menuService;

    @MockBean
    private SysMenuMapper menuMapper;

    @MockBean
    private EntityPermissionCatalogService entityPermissionCatalogService;

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
    @DisplayName("测试查询菜单树接口")
    void testTree() throws Exception {
        when(menuService.getMenuTree()).thenReturn(Collections.singletonList(testMenu));

        mockMvc.perform(get("/api/system/menu/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].menuName").value("测试菜单"));
    }

    @Test
    @DisplayName("测试查询运行态侧栏菜单树接口")
    void testSidebarTree() throws Exception {
        when(menuService.getSidebarMenuTree()).thenReturn(Collections.singletonList(testMenu));

        mockMvc.perform(get("/api/system/menu/sidebar-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].menuName").value("测试菜单"));
    }

    @Test
    @DisplayName("测试根据ID查询菜单接口-成功")
    void testGetById_Success() throws Exception {
        when(menuService.getById("test-id-1")).thenReturn(testMenu);

        mockMvc.perform(get("/api/system/menu/{id}", "test-id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.menuName").value("测试菜单"))
                .andExpect(jsonPath("$.data.perm").value("test:menu:list"));
    }

    @Test
    @DisplayName("测试根据ID查询菜单接口-不存在")
    void testGetById_NotFound() throws Exception {
        when(menuService.getById("non-existent")).thenReturn(null);

        mockMvc.perform(get("/api/system/menu/{id}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("测试新增菜单接口-成功")
    void testSave_Success() throws Exception {
        when(menuService.saveMenu(any(SysMenu.class))).thenAnswer(invocation -> {
            SysMenu menu = invocation.getArgument(0);
            menu.setId("new-id");
            return menu;
        });

        mockMvc.perform(post("/api/system/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testMenu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.menuName").value("测试菜单"));
    }

    @Test
    @DisplayName("测试新增菜单接口-权限标识重复")
    void testSave_DuplicatePerm() throws Exception {
        when(menuService.saveMenu(any(SysMenu.class)))
                .thenThrow(new RuntimeException("权限标识已存在：test:menu:list"));

        mockMvc.perform(post("/api/system/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testMenu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("权限标识已存在")));
    }

    @Test
    @DisplayName("测试更新菜单接口-成功")
    void testUpdate_Success() throws Exception {
        when(menuService.saveMenu(any(SysMenu.class))).thenAnswer(invocation -> {
            SysMenu menu = invocation.getArgument(0);
            return menu;
        });

        SysMenu updateMenu = new SysMenu();
        updateMenu.setMenuName("更新后的菜单");
        updateMenu.setMenuType("C");
        updateMenu.setPath("/test/menu");
        updateMenu.setPerm("test:menu:list");

        mockMvc.perform(put("/api/system/menu/{id}", "test-id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateMenu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.menuName").value("更新后的菜单"));
    }

    @Test
    @DisplayName("测试删除菜单接口-成功")
    void testDelete_Success() throws Exception {
        doNothing().when(menuService).deleteMenu("test-id-1");

        mockMvc.perform(delete("/api/system/menu/{id}", "test-id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).deleteMenu("test-id-1");
    }

    @Test
    @DisplayName("测试删除菜单接口-菜单不存在")
    void testDelete_NotFound() throws Exception {
        doThrow(new RuntimeException("菜单不存在")).when(menuService).deleteMenu("non-existent");

        mockMvc.perform(delete("/api/system/menu/{id}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("菜单不存在"));
    }

    @Test
    @DisplayName("测试更新菜单状态接口")
    void testUpdateStatus() throws Exception {
        doNothing().when(menuService).updateStatus("test-id-1", "1");

        mockMvc.perform(put("/api/system/menu/{id}/status", "test-id-1")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).updateStatus("test-id-1", "1");
    }

    @Test
    @DisplayName("测试更新菜单显示状态接口")
    void testUpdateVisible() throws Exception {
        doNothing().when(menuService).updateVisible("test-id-1", "1");

        mockMvc.perform(put("/api/system/menu/{id}/visible", "test-id-1")
                        .param("visible", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).updateVisible("test-id-1", "1");
    }

    @Test
    @DisplayName("测试更新菜单排序接口")
    void testUpdateSort() throws Exception {
        doNothing().when(menuService).updateSort(anyList());

        List<String> menuIds = Arrays.asList("id-2", "id-1");

        mockMvc.perform(put("/api/system/menu/sort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(menuIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).updateSort(menuIds);
    }

    @Test
    @DisplayName("测试导出菜单接口")
    void testExport() throws Exception {
        when(menuService.exportMenus()).thenReturn(Collections.singletonList(testMenu));

        mockMvc.perform(get("/api/system/menu/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].menuName").value("测试菜单"));
    }

    @Test
    @DisplayName("测试导入菜单接口")
    void testImportMenus() throws Exception {
        doNothing().when(menuService).importMenus(anyList());

        SysMenu importMenu = new SysMenu();
        importMenu.setMenuName("导入菜单");
        importMenu.setMenuType("C");
        importMenu.setPath("/import/menu");
        importMenu.setPerm("import:menu:list");

        mockMvc.perform(post("/api/system/menu/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singletonList(importMenu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).importMenus(anyList());
    }

    @Test
    @DisplayName("测试获取菜单类型选项接口")
    void testGetTypeOptions() throws Exception {
        List<Map<String, String>> options = Arrays.asList(
                Map.of("value", "M", "label", "目录"),
                Map.of("value", "C", "label", "菜单"),
                Map.of("value", "F", "label", "按钮")
        );
        when(menuService.getMenuTypeOptions()).thenReturn(options);

        mockMvc.perform(get("/api/system/menu/type-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].value").value("M"))
                .andExpect(jsonPath("$.data[0].label").value("目录"))
                .andExpect(jsonPath("$.data[1].value").value("C"))
                .andExpect(jsonPath("$.data[1].label").value("菜单"))
                .andExpect(jsonPath("$.data[2].value").value("F"))
                .andExpect(jsonPath("$.data[2].label").value("按钮"));
    }

    @Test
    @DisplayName("测试新增子菜单接口")
    void testSaveChildMenu() throws Exception {
        when(menuService.saveMenu(any(SysMenu.class))).thenAnswer(invocation -> {
            SysMenu menu = invocation.getArgument(0);
            menu.setId("child-id");
            return menu;
        });

        SysMenu childMenu = new SysMenu();
        childMenu.setMenuName("子菜单");
        childMenu.setMenuType("C");
        childMenu.setPath("/parent/child");
        childMenu.setPerm("parent:child:list");
        childMenu.setParentId("parent-id");

        mockMvc.perform(post("/api/system/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childMenu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.parentId").value("parent-id"));
    }

    @Test
    @DisplayName("测试删除父菜单-级联删除子菜单")
    void testDelete_CascadeDelete() throws Exception {
        doNothing().when(menuService).deleteMenu("parent-id");

        mockMvc.perform(delete("/api/system/menu/{id}", "parent-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).deleteMenu("parent-id");
    }

    @Test
    @DisplayName("测试新增按钮类型菜单")
    void testSaveButtonMenu() throws Exception {
        when(menuService.saveMenu(any(SysMenu.class))).thenAnswer(invocation -> {
            SysMenu menu = invocation.getArgument(0);
            menu.setId("button-id");
            return menu;
        });

        SysMenu buttonMenu = new SysMenu();
        buttonMenu.setMenuName("新增按钮");
        buttonMenu.setMenuType("F");
        buttonMenu.setPerm("test:menu:add");
        buttonMenu.setParentId("0");

        mockMvc.perform(post("/api/system/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buttonMenu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.menuType").value("F"));
    }
}
