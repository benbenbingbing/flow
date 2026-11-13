package com.workflow.admin.organization.application;

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

/** 真实数据库分页验证选择器的层级边界、停用祖先、定向回显和请求上限。 */
class OrganizationSelectionPaginationTest {
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
    void selectionBrowsesOnlyOneLevelAndKeepsDisabledAncestorsExpandable() {
        jdbc.update("UPDATE sys_organization SET status='1' WHERE id='group'");
        var roots = service.getChildren("0", null, null);
        assertEquals(List.of("group", "other"), ids(roots));
        assertTrue(roots.get(0).isHasChildren());
        assertEquals("1", roots.get(0).getStatus());
        var children = service.getChildren("group", null, null);
        assertEquals(55, children.size());
        assertFalse(ids(children).contains("nested"));
        assertEquals(List.of("nested"), ids(service.getChildren("dept54", null, null)));
        assertEquals(List.of("other"), ids(service.getChildren("0", "org", "group")));
        // 类型只约束搜索和选择，不能在浏览时剔除部门的组织祖先。
        assertEquals(List.of("nested"), ids(service.getSelectionPage(1, 20, "0", "深层", "dept").getRecords()));
        assertEquals(0, service.getSelectionPage(1, 20, "0", "集团", "org").getRecords().stream()
                .filter(row -> row.getId().equals("group")).count());
        assertEquals(0, service.getSelectionPage(1, 20, "other", "研发", "dept").getTotal());
        assertEquals(0, service.getSelectionPage(1, 20, "0", "研发", "org").getTotal());
    }

    @Test
    void selectionResolvesOnlyRequestedValuesAndPreservesDisabledLabels() {
        jdbc.update("UPDATE sys_organization SET org_code='GROUP_CODE', status='1' WHERE id='group'");
        jdbc.update("UPDATE sys_organization SET org_code=NULL WHERE id='other'");
        assertEquals(List.of(), service.resolveSelection(List.of(), "id"));
        assertEquals(List.of(), service.resolveSelection(List.of(" "), "id"));
        assertEquals(List.of("group"), ids(service.resolveSelection(List.of("group"), "id")));
        assertEquals("1", service.resolveSelection(List.of("GROUP_CODE"), "code").get(0).getStatus());
        assertEquals(List.of("other"), ids(service.resolveSelection(List.of("other"), "code")));
        assertEquals("集团 / 研发部 / 深层研发", service.resolveSelection(List.of("nested"), "id").get(0).getDisplayPath());
        jdbc.update("UPDATE sys_organization SET deleted=1 WHERE id='nested'");
        assertTrue(service.resolveSelection(List.of("nested"), "id").isEmpty());
        jdbc.update("UPDATE sys_organization SET hierarchy_ready=0 WHERE id='other'");
        assertTrue(service.resolveSelection(List.of("other"), "id").isEmpty());
    }

    @Test
    void selectionRejectsUnboundedRequests() {
        assertThrows(IllegalArgumentException.class, () -> service.getSelectionPage(1, 101, "0", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getSelectionPage(0, 20, "0", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getSelectionPage(1, 0, "0", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getSelectionPage(1, 20, "missing", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getSelectionPage(1, 20, "0", null, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.getChildren("missing", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getChildren("0", "invalid", null));
        assertThrows(IllegalArgumentException.class, () -> service.resolveSelection(List.of("group"), "invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.resolveSelection(java.util.Collections.nCopies(101, "group"), "id"));
    }

    @Test
    void largeSiblingLevelIsCompleteWithoutPreloadingDescendants() {
        for (int i = 0; i < 1001; i++) {
            String id = "large" + i;
            insert(id, "other", "dept", "同级部门" + i, "/0/other/" + id + "/");
        }
        insert("deep", "large1000", "dept", "下级部门", "/0/other/large1000/deep/");
        var children = service.getChildren("other", null, null);
        assertEquals(1001, children.size());
        assertFalse(ids(children).contains("deep"));
        assertTrue(children.stream().filter(row -> row.getId().equals("large1000")).findFirst().orElseThrow().isHasChildren());
        assertEquals("另一集团 / 同级部门1000", children.stream().filter(row -> row.getId().equals("large1000"))
                .findFirst().orElseThrow().getDisplayPath());
    }

    private void insert(String id, String parentId, String type, String name, String path) {
        jdbc.update("INSERT INTO sys_organization(id,org_code,org_name,type,parent_id,path) VALUES(?,?,?,?,?,?)",
                id, id, name, type, parentId, path);
    }

    private List<String> ids(List<SysOrganization> rows) {
        return rows.stream().map(SysOrganization::getId).toList();
    }
}
