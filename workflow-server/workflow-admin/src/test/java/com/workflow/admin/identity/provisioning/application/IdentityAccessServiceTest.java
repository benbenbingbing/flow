package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.identity.position.application.PositionOrganizationScopeService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import org.junit.jupiter.api.*;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityAccessServiceTest {
    JdbcTemplate jdbc;
    IdentityWriteProperties properties;
    CurrentUserRoleService roles;
    PositionOrganizationScopeService scope;
    IdentityAccessService access;

    @BeforeEach
    void setup() throws Exception {
        UserContext.clear();
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addMapper(IdentityPersistenceMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        var persistence = new SqlSessionTemplate(factory.getObject()).getMapper(IdentityPersistenceMapper.class);
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(64), status CHAR(1), deleted INT, org_id VARCHAR(64), dept_id VARCHAR(64))");
        jdbc.update("INSERT INTO sys_user VALUES ('svc', '0', 0, 'org', NULL), ('target', '0', 0, 'org', 'dept')");
        properties = new IdentityWriteProperties();
        var source = new IdentityWriteProperties.Source();
        source.setServiceUserId("svc"); source.setWritePermission("directory:write");
        properties.getSources().put("erp", source);
        roles = mock(CurrentUserRoleService.class);
        scope = mock(PositionOrganizationScopeService.class);
        access = new IdentityAccessService(properties, mock(IdentitySourceStore.class), persistence, roles, scope);
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test
    void requiresExplicitSourceAndPermissionEvenForConfiguredServiceAccount() {
        try (var permissions = mockStatic(PermissionUtil.class)) {
            permissions.when(() -> PermissionUtil.getUserPermissions("svc")).thenReturn(Set.of());
            assertEquals("FORBIDDEN", assertThrows(IdentityWriteException.class,
                    () -> access.requireSource(new ExternalIdentityKey("erp", "E1"))).code());
            permissions.when(() -> PermissionUtil.getUserPermissions("svc")).thenReturn(Set.of("directory:write"));
            assertEquals("svc", access.requireSource(new ExternalIdentityKey("erp", "E1")).actorId());
            assertEquals("SOURCE_NOT_ALLOWED", assertThrows(IdentityWriteException.class,
                    () -> access.requireSource(new ExternalIdentityKey("unknown", "E1"))).code());
            jdbc.update("UPDATE sys_user SET status = '1' WHERE id = 'svc'");
            assertEquals("FORBIDDEN", assertThrows(IdentityWriteException.class,
                    () -> access.requireSource(new ExternalIdentityKey("erp", "E1"))).code());
        }
    }
    @Test
    void unauthenticatedCallsWithoutServiceAccountCannotWrite() {
        properties.getSources().get("erp").setServiceUserId(null);
        assertEquals("FORBIDDEN", assertThrows(IdentityWriteException.class,
                () -> access.requireSource(new ExternalIdentityKey("erp", "E1"))).code());
        assertThrows(IdentityWriteException.class, () -> access.requireObject(IdentityObjectType.USER, "target"));
    }
    @Test
    void localProfileAccessChecksTargetDepartmentScope() {
        UserContext.setCurrentUser("svc", "service");
        assertEquals("svc", access.requireObject(IdentityObjectType.USER, "target"));
        verify(scope).requireVisible("dept");
        doThrow(new IllegalArgumentException("out of scope")).when(scope).requireVisible("dept");
        assertThrows(IllegalArgumentException.class, () -> access.requireObject(IdentityObjectType.USER, "target"));
    }
}
