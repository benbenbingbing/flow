package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/** 不连接数据库，仅验证注解 Mapper 的动态 SQL 能被 MyBatis 完整解析。 */
class EmbedManagementMapperContractTest {

    @Test
    void allAnnotatedStatementsCanBeRegistered() {
        Configuration configuration = new Configuration();

        configuration.addMapper(EmbedManagementMapper.class);

        String namespace = EmbedManagementMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findViews"));
        assertTrue(configuration.hasStatement(namespace + "findListTarget"));
        assertTrue(configuration.hasStatement(namespace + "findBindingByDigests"));
        assertTrue(configuration.hasStatement(namespace + "changeProviderStatus"));
    }

    @Test
    void viewRecordQueriesUseConstructorCompatibleExplicitColumnOrder() {
        for (String methodName : new String[]{
                "findViews", "findView", "lockView", "findViewByKey"}) {
            Method method = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            String sql = String.join("\n", method.getAnnotation(Select.class).value());

            assertFalse(sql.contains("v.*"), methodName + " 不能依赖物理表列序");
            assertTrue(sql.indexOf("v.published_release_id")
                            < sql.indexOf("r.revision AS published_revision"),
                    methodName + " 的 publishedRevision 必须紧跟 publishedReleaseId");
            assertTrue(sql.indexOf("r.revision AS published_revision")
                            < sql.indexOf("v.lock_version"),
                    methodName + " 必须与 ViewRow canonical constructor 列序一致");
        }
    }

    @Test
    void recordQueriesNeverDependOnPhysicalTableColumnOrder() {
        Set<String> recordQueries = Set.of(
                "findReleases", "findRelease",
                "findGrant", "lockGrant", "findGrants",
                "findProviders", "findProvider", "lockProvider",
                "findProviderByIssuerAndNamespace", "lockProviderByIssuerAndNamespace",
                "findBindings", "findBinding", "lockBinding", "findBindingByDigests");

        Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                .filter(method -> recordQueries.contains(method.getName()))
                .forEach(method -> {
                    String sql = String.join("\n", method.getAnnotation(Select.class).value());
                    assertFalse(sql.matches("(?is).*SELECT\\s+(?:[a-z]+\\.)?\\*.*"),
                            method.getName() + " 不能使用 SELECT * 映射 Java record");
                });
    }

    @Test
    void publishedResourceJoinsNormalizeLegacyCollationsExplicitly() {
        for (String methodName : new String[]{"findListTarget", "findFormTarget"}) {
            Method method = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            String sql = String.join("\n", method.getAnnotation(Select.class).value());

            // V001 中 entity_definition/entity_form 使用 0900_ai_ci，而 list/release 使用
            // unicode_ci；跨列 JOIN 不显式统一会在 MySQL 8 报 Illegal mix of collations。
            assertTrue(sql.contains("COLLATE utf8mb4_unicode_ci"));
        }
    }
}
