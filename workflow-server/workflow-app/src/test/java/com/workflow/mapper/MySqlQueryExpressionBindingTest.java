package com.workflow.mapper;

import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Pattern;

import static com.workflow.mapper.DatabasePaginationMapperContractTest.renderWrapper;
import static org.junit.jupiter.api.Assertions.*;

/** SQL 与 Wrapper 搜索都必须绑定完整模式，覆盖数字文本、NULL、空串、引号和通配符。 */
class MySqlQueryExpressionBindingTest {
    private static final String[] INPUTS = {null, "", "10", "O'Neil%\\_中文"};

    @Test
    void remainingSqlSearchQueriesBindCompletePatterns() throws Exception {
        var config = new Configuration();
        config.setDatabaseId("MYSQL");
        var names = List.of(
                "com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper",
                "com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper",
                "com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper",
                "com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper",
                "com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper",
                "com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper",
                "com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper",
                "com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper");
        Set<String> queries = new HashSet<>();
        for (String name : names) {
            for (var method : Class.forName(name).getDeclaredMethods()) {
                var select = method.getAnnotation(Select.class);
                if (select == null) continue;
                String sql = String.join(" ", select.value());
                if (!sql.contains("_contains_")) continue;
                String query = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                queries.add(query);
                var matcher = Pattern.compile("name=\"_contains_([A-Za-z0-9_]+)\"").matcher(sql);
                Set<String> inputs = new HashSet<>();
                while (matcher.find()) inputs.add(matcher.group(1));
                assertFalse(inputs.isEmpty(), query);
                for (String value : INPUTS) {
                    var params = new HashMap<String, Object>();
                    for (var parameter : method.getParameters()) {
                        Param param = parameter.getAnnotation(Param.class);
                        if (param != null) params.put(param.value(), null);
                    }
                    params.put("limit", 20);
                    params.put("offset", 0);
                    inputs.forEach(input -> params.put(input, value));
                    var bound = new XMLLanguageDriver().createSqlSource(config, sql, Map.class).getBoundSql(params);
                    for (String input : inputs) {
                        assertEquals(value == null ? null : "%" + value + "%",
                                bound.getAdditionalParameter("_contains_" + input), query);
                    }
                    bound.getParameterMappings().stream()
                            .filter(mapping -> mapping.getProperty().startsWith("_contains_"))
                            .forEach(mapping -> assertEquals(JdbcType.VARCHAR, mapping.getJdbcType(), query));
                    assertFalse(bound.getSql().contains("CONCAT"), query);
                    assertFalse(bound.getSql().contains("O'Neil"), query);
                }
            }
        }
        // 原 19 个搜索入口拆为这 12 个复杂 SQL 和下方逐个调用的 7 个 Wrapper；不能只降低数量阈值。
        assertEquals(Set.of(
                "SysUserMapper.selectPageByRoleId", "SysUserMapper.selectUserPage",
                "SysPositionMapper.countProcessReferences", "SysPositionAssignmentMapper.selectAssignmentPage",
                "EmbedManagementMapper.findViewsPage", "EmbedManagementMapper.countViews",
                "EmbedManagementMapper.findApplicationOptionsPage", "EmbedManagementMapper.countApplicationOptions",
                "EmbedManagementMapper.findIdentityProviderOptionsPage", "EmbedManagementMapper.countIdentityProviderOptions",
                "EmbedManagementMapper.findProvidersPage", "EmbedManagementMapper.countProviders"), queries);
    }

    @Test
    void migratedWrapperSearchQueriesKeepAllOriginalInputCases() {
        for (String value : INPUTS) {
            int optionalPatterns = value == null || value.isEmpty() ? 0 : 1;
            verifyPatterns("cc list", renderWrapper(ProcessCcRecordMapper.class,
                    mapper -> mapper.findByCcUserIdFiltered("user", value, value, null, null, 1, 3)),
                    value, 6 * optionalPatterns, false);
            verifyPatterns("cc count", renderWrapper(ProcessCcRecordMapper.class,
                    mapper -> mapper.countByCcUserIdFiltered("user", value, value, null, null)),
                    value, 6 * optionalPatterns, false);
            verifyPatterns("sla list", renderWrapper(ProcessTaskSlaMapper.class,
                    mapper -> mapper.findMonitorPage(null, null, null, value, 1, 3)),
                    value, 3 * optionalPatterns, false);
            verifyPatterns("sla count", renderWrapper(ProcessTaskSlaMapper.class,
                    mapper -> mapper.countMonitor(null, null, null, value)),
                    value, 3 * optionalPatterns, false);
            verifyPatterns("active release references", renderWrapper(UiConfigReleaseMapper.class,
                    mapper -> mapper.findActiveReferenceCandidates(value, value)),
                    value, value == null ? 0 : 2, value == null);
            verifyPatterns("executable release references", renderWrapper(UiConfigReleaseMapper.class,
                    mapper -> mapper.findExecutableDataSourceReferenceCandidates(value)),
                    value, value == null ? 0 : 1, value == null);
            verifyPatterns("draft references", renderWrapper(UiEventBindingMapper.class,
                    mapper -> mapper.findDraftReferenceCandidates(value, value)),
                    value, value == null ? 0 : 2, value == null);
        }
    }

    @Test
    void referenceSearchKeepsTheNonNullBranchWhenTheOtherNeedleIsNull() {
        for (String value : List.of("", "10", "O'Neil%\\_中文")) {
            verifyPatterns("release spaced only", renderWrapper(UiConfigReleaseMapper.class,
                    mapper -> mapper.findActiveReferenceCandidates(null, value)), value, 1, false);
            verifyPatterns("release compact only", renderWrapper(UiConfigReleaseMapper.class,
                    mapper -> mapper.findActiveReferenceCandidates(value, null)), value, 1, false);
            verifyPatterns("draft spaced only", renderWrapper(UiEventBindingMapper.class,
                    mapper -> mapper.findDraftReferenceCandidates(null, value)), value, 1, false);
            verifyPatterns("draft compact only", renderWrapper(UiEventBindingMapper.class,
                    mapper -> mapper.findDraftReferenceCandidates(value, null)), value, 1, false);
        }
    }

    /** 按 SQL 中每个 LIKE 位置核对参数值，不能只检查 Wrapper 的内存参数表。 */
    private static void verifyPatterns(String name, DatabasePaginationMapperContractTest.BoundQuery query,
            String input, int expectedPatterns, boolean shortCircuit) {
        if (shortCircuit) {
            assertNull(query, name + " 的全 NULL 引用应直接返回空结果");
            return;
        }
        assertNotNull(query, name);
        String sql = query.bound().getSql();
        assertFalse(sql.contains("O'Neil"), name);
        assertFalse(sql.contains("CONCAT"), name);
        assertFalse(sql.contains("#{") || sql.contains("${"), name);
        assertEquals(expectedPatterns, Pattern.compile("\\bLIKE\\s+\\?", Pattern.CASE_INSENSITIVE)
                .matcher(sql).results().count(), name);
        String expected = input == null ? null : "%" + input + "%";
        long patternBindings = query.values().stream().filter(value -> expected != null && expected.equals(value)).count();
        assertEquals(expectedPatterns, patternBindings, name + " 应保持完整字符串模式，不转成数字或拼入 SQL");
    }
}
