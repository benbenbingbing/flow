package com.workflow.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** 用实际 MP 映射检查查询范围、分页上限和原子更新，避免 Wrapper 重构改变凭据管理约束。 */
class IntegrationMapperWrapperContractTest {
    private final MybatisConfiguration configuration = configuration();

    @Test
    void applicationUpdatesKeepVersionGuardAndExplicitNullAssignments() {
        var mapper = mock(IntegrationApplicationMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            BoundSql bound = bound(IntegrationApplicationMapper.class, "update", invocation.getArgument(1));
            String sql = compact(bound);
            assertTrue(sql.contains("version=version+1"));
            assertTrue(sql.contains("updated_by=?"));
            assertTrue(sql.contains("update_time=?"));
            assertTrue(sql.contains("where(id=?andversion=?)"));
            List<Object> values = values(bound);
            assertEquals(2, values.stream().filter(value -> value == null).count());
            assertTrue(values.contains("app-1"));
            assertTrue(values.contains(7L));
            return 1;
        }).when(mapper).update(isNull(), any());

        assertEquals(1, mapper.updateStatus("app-1", "DISABLED", 7, null, null));
        assertEquals(1, mapper.advanceVersion("app-1", 7, null, null));
        verify(mapper, times(2)).update(isNull(), any());
    }

    @Test
    void credentialBatchCannotWidenEmptyScopesAndVersionIncludesRevokedHistory() {
        var mapper = mock(IntegrationCredentialMapper.class, CALLS_REAL_METHODS);
        assertTrue(mapper.findActiveByApplicationIds(List.of()).isEmpty());
        assertTrue(mapper.findActiveByApplicationIds(null).isEmpty());
        verify(mapper, never()).selectList(any());
        doAnswer(invocation -> {
            BoundSql bound = bound(IntegrationCredentialMapper.class, "selectList", invocation.getArgument(0));
            String sql = compact(bound);
            assertTrue(sql.contains("status=?andapplication_idin(?,?)"));
            assertTrue(sql.contains("orderbyapplication_idasc"));
            assertEquals(List.of("ACTIVE", "app-1", "app-2"), values(bound));
            return List.of();
        }).when(mapper).selectList(any());
        mapper.findActiveByApplicationIds(List.of("app-1", "app-2"));

        doAnswer(invocation -> {
            BoundSql bound = bound(IntegrationCredentialMapper.class, "selectObjs", invocation.getArgument(0));
            String sql = compact(bound);
            assertTrue(sql.contains("selectmax(credential_version)"));
            assertFalse(sql.contains("status"), "撤销的凭据也必须推进下一次签发版本");
            assertEquals(List.of("app-1"), values(bound));
            return Arrays.asList((Object) null);
        }).when(mapper).selectObjs(any());
        assertEquals(0, mapper.findLatestVersion("app-1"));
        doReturn(List.of(4294967296L)).when(mapper).selectObjs(any());
        assertEquals(4294967296L, mapper.findLatestVersion("app-1"));
    }

    @Test
    void revocationKeepsApplicationAndActiveStateInOneUpdate() {
        var mapper = mock(IntegrationCredentialMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            BoundSql bound = bound(IntegrationCredentialMapper.class, "update", invocation.getArgument(1));
            String sql = compact(bound);
            assertTrue(sql.contains("setstatus=?,revoked_by=?,revoked_at=?"));
            assertTrue(sql.contains("where(application_id=?andstatus=?)"));
            assertEquals(Arrays.asList("REVOKED", null, null, "app-1", "ACTIVE"), values(bound));
            return 1;
        }).when(mapper).update(isNull(), any());
        assertEquals(1, mapper.revokeActive("app-1", null, null));
    }

    @Test
    void credentialUsageBindsNullableValuesAndUsesOnePortableUpdate() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", null);
        parameters.put("now", null);
        BoundSql bound = configuration.getMappedStatement(
                IntegrationCredentialMapper.class.getName() + ".markActiveUsedByClientId")
                .getBoundSql(parameters);
        String sql = compact(bound);
        assertTrue(sql.startsWith("updateintegration_application_credentialsetlast_used_at=?"));
        assertTrue(sql.contains("wherestatus='active'andapplication_idin("));
        assertTrue(sql.contains("selectidfromintegration_applicationwhereclient_id=?andstatus='active'"));
        assertFalse(sql.contains("join"));
        assertEquals(Arrays.asList(null, null), values(bound));
        assertEquals(List.of(JdbcType.TIMESTAMP, JdbcType.VARCHAR), bound.getParameterMappings().stream()
                .map(mapping -> mapping.getJdbcType()).toList());
    }

    @Test
    void recentAndFirstRowQueriesPassTheirOriginalLimitsToThePaginationPlugin() {
        var applications = mock(IntegrationApplicationMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            IPage<IntegrationApplicationRecord> page = invocation.getArgument(0);
            Wrapper<IntegrationApplicationRecord> wrapper = invocation.getArgument(1);
            assertEquals(0, page.offset());
            assertFalse(page.searchCount());
            String sql = compact(bound(IntegrationApplicationMapper.class, "selectList", wrapper));
            if (page.getSize() == 200) {
                assertTrue(sql.contains("orderbycreate_timedesc,iddesc"));
            } else {
                assertEquals(1, page.getSize());
                assertTrue(sql.contains("client_id=?"));
            }
            return page;
        }).when(applications).selectPage(any(), any());
        assertTrue(applications.findRecent().isEmpty());
        assertNull(applications.findByClientId("client-1"));

        var credentials = mock(IntegrationCredentialMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            IPage<IntegrationApplicationCredentialRecord> page = invocation.getArgument(0);
            assertEquals(1, page.getSize());
            assertFalse(page.searchCount());
            BoundSql bound = bound(IntegrationCredentialMapper.class, "selectList", invocation.getArgument(1));
            assertEquals(List.of("app-1", "ACTIVE"), values(bound));
            return page;
        }).when(credentials).selectPage(any(), any());
        assertNull(credentials.findActive("app-1"));
    }

    private static MybatisConfiguration configuration() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(IntegrationApplicationMapper.class);
        configuration.addMapper(IntegrationCredentialMapper.class);
        return configuration;
    }

    private BoundSql bound(Class<?> mapper, String method, Wrapper<?> wrapper) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("ew", wrapper);
        parameters.put("et", null);
        return configuration.getMappedStatement(mapper.getName() + "." + method).getBoundSql(parameters);
    }

    private List<Object> values(BoundSql bound) {
        var parameters = configuration.newMetaObject(bound.getParameterObject());
        return bound.getParameterMappings().stream().map(mapping -> parameters.getValue(mapping.getProperty())).toList();
    }

    private static String compact(BoundSql bound) {
        return bound.getSql().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
