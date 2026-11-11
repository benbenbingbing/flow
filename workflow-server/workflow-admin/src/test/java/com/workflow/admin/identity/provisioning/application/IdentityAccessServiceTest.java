package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.identity.position.application.PositionOrganizationScopeService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    /** 历史配置未声明开关时继续要求来源写权限，空值不能隐式开启免权限模式。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void sourcePermissionCheckRemainsEnabledByDefault(String permission) {
        var source = properties.getSources().get("erp");
        assertTrue(source.isPermissionCheckEnabled());
        source.setWritePermission(permission);
        assertEquals("SOURCE_NOT_ALLOWED", assertThrows(IdentityWriteException.class,
                () -> access.requireSource(new ExternalIdentityKey("erp", "E1"))).code());
    }

    /** 显式关闭后来源权限可省略，也不因保留旧权限码而继续查询权限集合。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"directory:write", "  "})
    void disabledSourcePermissionCheckAcceptsValidServiceAccount(String permission) {
        var source = properties.getSources().get("erp");
        source.setPermissionCheckEnabled(false);
        source.setWritePermission(permission);
        try (var permissions = mockStatic(PermissionUtil.class)) {
            var writer = access.requireSource(new ExternalIdentityKey("erp", "E1"));
            assertEquals("svc", writer.actorId());
            assertSame(source, writer.source());
            permissions.verifyNoInteractions();
        }
    }

    /** 有登录上下文时继续使用当前操作者，供后续基础资料及审计归属使用。 */
    @Test
    void disabledSourcePermissionCheckKeepsLoggedInActor() {
        var source = properties.getSources().get("erp");
        source.setPermissionCheckEnabled(false);
        source.setWritePermission(null);
        source.setServiceUserId(null);
        UserContext.setCurrentUser("target", "target-user");
        try (var permissions = mockStatic(PermissionUtil.class)) {
            assertEquals("target", access.requireSource(new ExternalIdentityKey("erp", "E1")).actorId());
            permissions.verifyNoInteractions();
        }
    }

    /** 来源免权限模式仍要求有效账号，缺失、停用和删除的服务账号均不能承担写入与审计。 */
    @ParameterizedTest
    @CsvSource(value = {"NULL,0,0", "missing,0,0", "svc,1,0", "svc,0,1"}, nullValues = "NULL")
    void disabledSourcePermissionCheckRejectsInvalidServiceAccount(String serviceUserId, String status, int deleted) {
        var source = properties.getSources().get("erp");
        source.setPermissionCheckEnabled(false);
        source.setWritePermission(null);
        source.setServiceUserId(serviceUserId);
        jdbc.update("UPDATE sys_user SET status = ?, deleted = ? WHERE id = 'svc'", status, deleted);
        assertEquals("FORBIDDEN", assertThrows(IdentityWriteException.class,
                () -> access.requireSource(new ExternalIdentityKey("erp", "E1"))).code());
    }

    @Test
    void disabledSourcePermissionCheckRejectsInvalidCurrentActorWithoutServiceFallback() {
        properties.getSources().get("erp").setPermissionCheckEnabled(false);
        UserContext.setCurrentUser("missing", "missing-user");
        assertEquals("FORBIDDEN", assertThrows(IdentityWriteException.class,
                () -> access.requireSource(new ExternalIdentityKey("erp", "E1"))).code());
    }

    @Test
    void disabledSourcePermissionCheckStillRequiresRegisteredSourceAndValidKey() {
        properties.getSources().get("erp").setPermissionCheckEnabled(false);
        assertEquals("SOURCE_NOT_ALLOWED", assertThrows(IdentityWriteException.class,
                () -> access.requireSource(new ExternalIdentityKey("unknown", "E1"))).code());
        assertEquals("INVALID_MODEL", assertThrows(IdentityWriteException.class,
                () -> access.requireSource(new ExternalIdentityKey("erp", " "))).code());
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
