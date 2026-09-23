package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/** 不连接数据库，仅验证注解 Mapper 的动态 SQL 能被 MyBatis 完整解析。 */
class EmbedManagementMapperContractTest {

    @Test
    void allAnnotatedStatementsCanBeRegistered() {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("MYSQL");

        configuration.addMapper(EmbedManagementMapper.class);

        String namespace = EmbedManagementMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findViewsPage"));
        assertTrue(configuration.hasStatement(namespace + "findListTargetPage"));
        assertTrue(configuration.hasStatement(namespace + "findReleaseByConfigHashPage"));
        assertTrue(configuration.hasStatement(namespace + "findBindingByDigestsPage"));
        assertTrue(configuration.hasStatement(namespace + "changeProviderStatus"));
        assertTrue(configuration.hasStatement(namespace + "findApplicationOptionsPage"));
        assertTrue(configuration.hasStatement(namespace + "findIdentityProviderOptionsPage"));
    }

    @Test
    void optionQueriesHaveMinimalColumnsAndMatchingBoundCountFilters() {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("MYSQL");
        configuration.addMapper(EmbedManagementMapper.class);
        String namespace = EmbedManagementMapper.class.getName() + ".";
        for (String directory : new String[]{"Application", "IdentityProvider"}) {
            Map<String, Object> parameters = new HashMap<>(Map.of(
                    "keyword", "partner' OR 1=1 --", "status", "ACTIVE", "limit", 20, "offset", 20));
            String query = configuration.getMappedStatement(namespace + "find" + directory + "OptionsPage")
                    .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
            String count = configuration.getMappedStatement(namespace + "count" + directory + "Options")
                    .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();

            if (directory.equals("Application")) {
                assertTrue(query.startsWith(
                        "SELECT a.id, a.application_name AS name, a.client_id, a.status, "
                                + "a.expires_at, CASE WHEN"));
                assertTrue(query.contains("AS embed_launch_ready"));
                assertTrue(query.contains("LEFT JOIN integration_application_credential c"));
                assertTrue(query.contains("c.status = 'ACTIVE'"));
                assertTrue(query.contains(
                        "c.expires_at IS NULL OR c.expires_at > UTC_TIMESTAMP(6)"));
                assertFalse(query.contains("integration_application_scope"));
                assertFalse(query.contains("secret_hash"));
                assertFalse(query.contains("credential_hint"));
            } else {
                assertEquals("SELECT id, name, type, status",
                        query.substring(0, query.indexOf(" FROM")));
                assertFalse(query.contains("credential"));
            }
            assertEquals(count.substring(count.indexOf(" WHERE")),
                    query.substring(query.indexOf(" WHERE"), query.indexOf(" ORDER BY")));
            assertTrue(query.contains("id LIKE ?"));
            assertFalse(query.contains("CONCAT"));
            assertTrue(query.contains("status = ?"));
            assertTrue(query.contains("CASE WHEN") && query.contains("id = ? THEN 0 ELSE 1 END"),
                    "完整 ID 回查必须优先返回精确项");
            assertFalse(query.contains("LIMIT"));
            Method pageMethod = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals("find" + directory + "OptionsPage"))
                    .findFirst().orElseThrow();
            assertTrue(com.baomidou.mybatisplus.core.metadata.IPage.class.isAssignableFrom(pageMethod.getParameterTypes()[0]));
            assertFalse(query.contains("partner'"), "搜索输入必须作为绑定参数传递");
            assertFalse(query.contains("issuer"));
            assertFalse(query.contains("jwks"));

            parameters.put("keyword", null);
            parameters.put("status", null);
            String unfiltered = configuration.getMappedStatement(namespace + "find" + directory + "OptionsPage")
                    .getBoundSql(parameters).getSql();
            assertFalse(unfiltered.contains("LIKE"));
            assertFalse(unfiltered.contains("status = ?"));
        }
    }

    @Test
    void viewRecordQueriesUseConstructorCompatibleExplicitColumnOrder() {
        for (String methodName : new String[]{
                "findViews", "findView", "lockView", "findViewByKey"}) {
            Method method = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName + "Page")
                            || candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.isAnnotationPresent(Select.class))
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
                "findApplicationOptions", "findIdentityProviderOptions",
                "findReleaseByConfigHash",
                "findGrant", "lockGrant", "findGrants",
                "findProviders", "findProvider", "lockProvider",
                "findProviderByIssuerAndNamespace", "lockProviderByIssuerAndNamespace",
                "findBindings", "findBinding", "lockBinding", "findBindingByDigests");

        Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                .filter(method -> recordQueries.contains(method.getName())
                        || (method.getName().endsWith("Page")
                            && recordQueries.contains(method.getName().substring(0, method.getName().length() - 4))))
                .filter(method -> method.isAnnotationPresent(Select.class))
                .forEach(method -> {
                    String sql = String.join("\n", method.getAnnotation(Select.class).value());
                    assertFalse(sql.matches("(?is).*SELECT\\s+(?:[a-z]+\\.)?\\*.*"),
                            method.getName() + " 不能使用 SELECT * 映射 Java record");
                });
    }

    @Test
    void bindingQueriesProjectTheSameFlowUserReadinessAsRuntime() {
        for (String methodName : new String[]{
                "findBindings", "findBinding", "findBindingByDigests"}) {
            Method method = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName + "Page")
                            || candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.isAnnotationPresent(Select.class))
                    .findFirst()
                    .orElseThrow();
            String sql = String.join("\n", method.getAnnotation(Select.class).value());

            assertTrue(sql.contains("EXISTS (SELECT 1 FROM sys_user u"), methodName);
            assertTrue(sql.contains("u.id = b.flow_user_id"), methodName);
            assertTrue(sql.contains("u.status = '0'"), methodName);
            assertTrue(sql.contains("u.deleted = 0"), methodName);
            assertTrue(sql.contains("u.password_reset_required = 0"), methodName);
            assertTrue(sql.indexOf("AS flow_user_ready") < sql.indexOf("b.status"),
                    methodName + " 必须保持 BindingRow canonical constructor 列序");
        }
        Method lock = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("lockBinding"))
                .findFirst()
                .orElseThrow();
        String lockSql = String.join("\n", lock.getAnnotation(Select.class).value());
        assertTrue(lockSql.contains("0 AS flow_user_ready"));
        assertFalse(lockSql.contains("sys_user"),
                "Binding 状态锁不能额外锁定用户行或改变既有锁顺序");
    }

    @Test
    void publishedResourceJoinsUseDialectTextConversionWithoutHardcodedCollations() {
        for (String methodName : new String[]{
                "findListTarget",
                "findFormTarget"}) {
            Method method = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName + "Page")
                            || candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.isAnnotationPresent(Select.class))
                    .findFirst()
                    .orElseThrow();
            String sql = String.join("\n", method.getAnnotation(Select.class).value());

            // 运行库的比较规则由结构和连接统一配置，查询不能硬编码 MySQL 排序规则。
            assertFalse(sql.contains("COLLATE"));
            assertFalse(sql.contains("CHARACTER SET"));
            assertTrue(sql.contains("DatabaseQuerySql@integerIdentifierText(_databaseId, 'e.id')"));
        }
    }

    @Test
    void providerIssuerLookupUsesAnnotationSafeNullEquality() {
        for (String methodName : new String[]{
                "findProviderByIssuerAndNamespace",
                "lockProviderByIssuerAndNamespace"}) {
            Method method = Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName + "Page")
                            || candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.isAnnotationPresent(Select.class))
                    .findFirst()
                    .orElseThrow();
            String sql = String.join("\n", method.getAnnotation(Select.class).value());

            // 注解 SQL 不会像 XML Mapper 一样解码 &lt;=&gt;；直接写实体会把无效文本
            // 发送给 MySQL。显式展开 NULL 等价语义，兼容 SIGNED_JWT 与无 issuer 的受信模式。
            assertFalse(sql.contains("&lt;"), methodName + " 不能包含 XML 实体");
            assertTrue(sql.contains("issuer = #{issuer,jdbcType=VARCHAR}"));
            assertTrue(sql.contains("issuer IS NULL AND #{issuer,jdbcType=VARCHAR} IS NULL"));
        }
    }

    @Test
    void annotatedSqlNeverContainsXmlComparisonEntities() {
        Arrays.stream(EmbedManagementMapper.class.getDeclaredMethods())
                .forEach(method -> Arrays.stream(method.getDeclaredAnnotations())
                        .map(EmbedManagementMapperContractTest::sqlFragments)
                        .flatMap(Arrays::stream)
                        .forEach(sql -> {
                            // 只有显式 <script> 注解会经过 XMLLanguageDriver；普通注解中的
                            // &lt;/&gt; 会原样进入 JDBC，并在真实 MySQL 查询时触发语法错误。
                            if (sql.stripLeading().startsWith("<script>")) {
                                return;
                            }
                            assertFalse(sql.contains("&lt;"), method.getName() + " 不能包含 &lt; 实体");
                            assertFalse(sql.contains("&gt;"), method.getName() + " 不能包含 &gt; 实体");
                        }));
    }

    private static String[] sqlFragments(Annotation annotation) {
        if (annotation instanceof Select select) {
            return select.value();
        }
        if (annotation instanceof Insert insert) {
            return insert.value();
        }
        if (annotation instanceof Update update) {
            return update.value();
        }
        if (annotation instanceof Delete delete) {
            return delete.value();
        }
        return new String[0];
    }
}
