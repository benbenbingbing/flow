package com.workflow.organization;

import com.workflow.admin.organization.application.SysOrganizationService;
import com.workflow.admin.organization.application.SysOrganizationServiceImpl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
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

/** 真实 Mapper 与分页插件联合验证管理树范围、逻辑删除和跨页稳定排序。 */
class OrganizationManagementPaginationTest {
    private JdbcTemplate jdbc;
    private SysOrganizationService service;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:org_page_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE sys_organization (
                  id VARCHAR(64) PRIMARY KEY, org_code VARCHAR(100), org_name VARCHAR(100),
                  type VARCHAR(20), business_level_code VARCHAR(100), parent_id VARCHAR(64),
                  hierarchy_ready SMALLINT DEFAULT 1, level INT, path VARCHAR(500), sort_order INT DEFAULT 0,
                  leader_id VARCHAR(64), leader_name VARCHAR(100), phone VARCHAR(50), email VARCHAR(100),
                  address VARCHAR(200), status VARCHAR(10) DEFAULT '0', description VARCHAR(500),
                  create_time TIMESTAMP DEFAULT '2026-01-01 00:00:00', create_by VARCHAR(64),
                  update_time TIMESTAMP, deleted INT DEFAULT 0)
                """);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SysOrganizationMapper.class);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(pagination);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(SysOrganizationMapper.class);
        service = new SysOrganizationServiceImpl(mapper, mock(SysUserMapper.class), mock(SysDictItemMapper.class));
        insert("group", "0", "org", "集团", "/0/group/");
        insert("other", "0", "org", "另一集团", "/0/other/");
        for (int i = 0; i < 55; i++) {
            String id = "dept%02d".formatted(i);
            insert(id, "group", "dept", "研发部", "/0/group/" + id + "/");
        }
        insert("nested", "dept54", "dept", "深层研发", "/0/group/dept54/nested/");
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void pagesOnlyDirectChildrenWithStableOrderAndKeepsDisabledRows() {
        jdbc.update("UPDATE sys_organization SET status='1' WHERE id='dept00'");
        var first = service.getManagementPage(1, 50, "group", "", "", null);
        var second = service.getManagementPage(2, 50, "group", null, null, null);
        assertEquals(55, first.getTotal());
        assertEquals(50, first.getRecords().size());
        assertEquals(5, second.getRecords().size());
        assertEquals("dept00", first.getRecords().get(0).getId());
        assertEquals("1", first.getRecords().get(0).getStatus());
        assertEquals("dept54", second.getRecords().get(4).getId());
        assertEquals("集团 / 研发部", second.getRecords().get(4).getDisplayPath());
        assertTrue(second.getRecords().get(4).isHasChildren());
        assertFalse(first.getRecords().get(0).isHasChildren());
        assertEquals(2, service.getManagementPage(1, 20, "0", null, null, null).getTotal());
    }

    @Test
    void searchUsesCurrentSubtreeAndCanMatchTheScopeNodeOrCode() {
        assertEquals(List.of("nested"), ids(service.getManagementPage(1, 20, "group", "深层", null, null).getRecords()));
        assertEquals(0, service.getManagementPage(1, 20, "other", "研发", null, null).getTotal());
        assertEquals(List.of("dept54"), ids(service.getManagementPage(1, 20, "dept54", "dept54", null, null).getRecords()));
        assertEquals(0, service.getManagementPage(1, 20, "group", "研发", "org", null).getTotal());
    }

    @Test
    void parentSelectionExcludesTheNodeAndItsDescendantsOnEveryPage() {
        var page = service.getManagementPage(1, 100, "0", "研发", null, "dept54");
        assertEquals(54, page.getTotal());
        assertFalse(ids(page.getRecords()).contains("nested"));
        assertFalse(ids(page.getRecords()).contains("dept54"));
        assertEquals(List.of("other"), ids(service.getManagementPage(1, 20, "0", null, "org", "group").getRecords()));
    }

    @Test
    void deletedAndUnresolvedNodesDoNotAppearOrCreateExpandArrows() {
        jdbc.update("UPDATE sys_organization SET deleted=1 WHERE id='nested'");
        jdbc.update("UPDATE sys_organization SET hierarchy_ready=0, path=NULL WHERE id='dept00'");
        var page = service.getManagementPage(1, 100, "group", null, null, null);
        assertEquals(54, page.getTotal());
        assertFalse(page.getRecords().stream().filter(row -> row.getId().equals("dept54")).findFirst().orElseThrow().isHasChildren());
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage(1, 20, "dept00", "研发", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getAncestors("nested"));
    }

    @Test
    void ancestorsIncludeTargetInPathOrderWithFullNames() {
        var rows = service.getAncestors("nested");
        assertEquals(List.of("group", "dept54", "nested"), ids(rows));
        assertEquals("集团 / 研发部 / 深层研发", rows.get(2).getDisplayPath());
    }

    @Test
    void rejectsInvalidPagingTypesAndUnknownScopeInsteadOfExpandingSearch() {
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage(0, 20, "0", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage(1, 101, "0", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage(1, 0, "0", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage(1, 20, "missing", "研发", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage(1, 20, "0", null, "invalid", null));
        assertThrows(IllegalArgumentException.class, () -> service.getManagementPage(1, 20, "0", null, null, "missing"));
    }

    private void insert(String id, String parentId, String type, String name, String path) {
        jdbc.update("INSERT INTO sys_organization(id,org_code,org_name,type,parent_id,path) VALUES(?,?,?,?,?,?)",
                id, id, name, type, parentId, path);
    }

    private List<String> ids(List<SysOrganization> rows) {
        return rows.stream().map(SysOrganization::getId).toList();
    }
}
