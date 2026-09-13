package com.workflow.migration.api.web;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.PageResult;
import com.workflow.migration.api.request.ConfigMigrationAssetQuery;
import com.workflow.migration.application.ConfigMigrationAssetService;
import com.workflow.migration.application.ConfigMigrationImportApplyService;
import com.workflow.migration.application.ConfigMigrationPackageService;
import com.workflow.migration.application.ConfigMigrationReadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConfigMigrationControllerTest {

    @Mock
    private ConfigMigrationAssetService assetService;
    @Mock
    private ConfigMigrationPackageService packageService;
    @Mock
    private ConfigMigrationImportApplyService importApplyService;
    @Mock
    private ConfigMigrationReadService readService;
    @Mock
    private SysMenuMapper menuMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        new PermissionUtil(
                mock(SysUserRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                menuMapper).init();
        UserContext.setCurrentUser("migration-admin", "迁移管理员");
        when(menuMapper.selectPermsByUserId("migration-admin"))
                .thenReturn(Set.of("*"));
        ConfigMigrationController controller = new ConfigMigrationController(
                assetService,
                packageService,
                importApplyService,
                readService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void legacyListEndpointsKeepArrayResponses() throws Exception {
        when(assetService.query(any())).thenReturn(List.of());
        when(packageService.listExports()).thenReturn(List.of());
        when(packageService.listImports()).thenReturn(List.of());

        mockMvc.perform(get("/api/config-migration/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/api/config-migration/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/api/config-migration/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void assetPageReturnsPageResultAndForwardsFilters() throws Exception {
        when(readService.pageAssets(any())).thenReturn(
                new PageResult<>(List.of(), 42, 2, 25));

        mockMvc.perform(get("/api/config-migration/assets/page")
                        .param("assetType", "ENTITY")
                        .param("businessKey", "expense")
                        .param("markForExport", "true")
                        .param("pageNum", "2")
                        .param("pageSize", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(42))
                .andExpect(jsonPath("$.data.pageNum").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(25));

        ArgumentCaptor<ConfigMigrationAssetQuery> captor =
                ArgumentCaptor.forClass(ConfigMigrationAssetQuery.class);
        verify(readService).pageAssets(captor.capture());
        ConfigMigrationAssetQuery query = captor.getValue();
        assertEquals("ENTITY", query.getAssetType());
        assertEquals("expense", query.getBusinessKey());
        assertEquals(true, query.getMarkForExport());
        assertEquals(2, query.getPageNum());
        assertEquals(25, query.getPageSize());
    }

    @Test
    void packageAndImportPageUseDefaultsAndExplicitPagination()
            throws Exception {
        when(readService.pageExports(1, 20)).thenReturn(
                new PageResult<>(List.of(), 3, 1, 20));
        when(readService.pageImports(3, 50)).thenReturn(
                new PageResult<>(List.of(), 101, 3, 50));

        mockMvc.perform(get("/api/config-migration/packages/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));
        mockMvc.perform(get("/api/config-migration/imports/page")
                        .param("pageNum", "3")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(101))
                .andExpect(jsonPath("$.data.pageNum").value(3))
                .andExpect(jsonPath("$.data.pageSize").value(50));

        verify(readService).pageExports(1, 20);
        verify(readService).pageImports(3, 50);
    }

    @Test
    void statsReturnsGlobalOverviewShape() throws Exception {
        when(readService.stats()).thenReturn(Map.of(
                "pending", 7L,
                "exported", 11L,
                "blocked", 3L));

        mockMvc.perform(get("/api/config-migration/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(7))
                .andExpect(jsonPath("$.data.exported").value(11))
                .andExpect(jsonPath("$.data.blocked").value(3));
    }

    @Test
    void importOptionsReturnLightweightArrayForCompareSelector()
            throws Exception {
        when(readService.listImportOptions()).thenReturn(List.of(Map.of(
                "id", "import-1",
                "migrationTag", "REL-001",
                "packageNo", "WFP-001")));

        mockMvc.perform(get("/api/config-migration/imports/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("import-1"))
                .andExpect(jsonPath("$.data[0].migrationTag")
                        .value("REL-001"));

        verify(readService).listImportOptions();
    }
}
