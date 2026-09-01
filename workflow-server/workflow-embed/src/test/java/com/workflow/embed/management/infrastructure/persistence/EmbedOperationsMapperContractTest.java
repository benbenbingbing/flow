package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/** Launch/Session 管理 SQL 的有界查询与脱敏静态合约。 */
class EmbedOperationsMapperContractTest {

    @Test
    void mapperRegistersAllDynamicStatements() {
        Configuration configuration = new Configuration();

        configuration.addMapper(EmbedOperationsMapper.class);

        String namespace = EmbedOperationsMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findLaunches"));
        assertTrue(configuration.hasStatement(namespace + "findSessions"));
        assertTrue(configuration.hasStatement(namespace + "findActiveSessionIdsByView"));
        assertTrue(configuration.hasStatement(namespace + "findActiveSessionIdsByApplication"));
    }

    @Test
    void managementProjectionsNeverSelectCredentialIdentityOrContextColumns() {
        for (String methodName : new String[]{
                "findLaunches", "findLaunch", "lockLaunch", "findSessions", "findSession"}) {
            String sql = selectSql(methodName).toLowerCase(java.util.Locale.ROOT);
            assertFalse(sql.contains("launch_code"), methodName);
            assertFalse(sql.contains("session_token"), methodName);
            assertFalse(sql.contains("subject_digest"), methodName);
            assertFalse(sql.contains("context_ciphertext"), methodName);
            assertFalse(sql.contains("nonce_digest"), methodName);
            assertFalse(sql.matches("(?is).*select\\s+(?:[a-z]+\\.)?\\*.*"), methodName);
        }
    }

    @Test
    void allCollectionQueriesHaveDeterministicCursorAndHardLimit() {
        for (String methodName : new String[]{
                "findLaunches", "findSessions",
                "findActiveSessionIdsByView", "findActiveSessionIdsByApplication"}) {
            String sql = selectSql(methodName);
            assertTrue(sql.contains("ORDER BY"), methodName);
            assertTrue(sql.contains("LIMIT #{fetchLimit}"), methodName);
        }
        assertTrue(selectSql("findLaunches").contains("id &lt; #{cursorId}"));
        assertTrue(selectSql("findSessions").contains("id &lt; #{cursorId}"));
        assertTrue(selectSql("findActiveSessionIdsByView")
                .contains("slot_released = 0"));
    }

    @Test
    void nonScriptSessionCursorsReachJdbcAsComparisonOperators() {
        Configuration configuration = new Configuration();
        configuration.addMapper(EmbedOperationsMapper.class);
        String namespace = EmbedOperationsMapper.class.getName() + ".";

        String byView = normalize(configuration
                .getMappedStatement(namespace + "findActiveSessionIdsByView")
                .getBoundSql(Map.of(
                        "viewId", "ev_1", "afterSessionId", "es_1", "fetchLimit", 2))
                .getSql());
        String byApplication = normalize(configuration
                .getMappedStatement(namespace + "findActiveSessionIdsByApplication")
                .getBoundSql(Map.of(
                        "applicationId", "app_1", "afterSessionId", "es_1", "fetchLimit", 2))
                .getSql());

        for (String sql : new String[]{byView, byApplication}) {
            assertTrue(sql.contains("id > ?"), sql);
            assertFalse(sql.contains("&gt;"), sql);
        }
    }

    private static String selectSql(String methodName) {
        Method method = Arrays.stream(EmbedOperationsMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ").trim();
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
