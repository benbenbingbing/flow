package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据权限手写 SQL 片段编译器测试。
 *
 * <p>覆盖主表别名改写、用户占位符替换、数据范围/适用对象差异校验，
 * 以及分号、注释、完整语句和危险关键字的拒绝。</p>
 */
class PermissionSqlFragmentCompilerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
    private PermissionSqlFragmentCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new PermissionSqlFragmentCompiler(jdbcTemplate, tableResolver);
        when(tableResolver.resolve("expense")).thenReturn("wf_expense");
    }

    @Test
    void compileRecordSqlRewritesBizAliasAndPlaceholders() {
        String sql = compiler.compileRecordSql(
                "expense",
                "biz.create_by = #{userId} AND biz.dept_id = #{deptId}",
                user());

        assertEquals(
                "`wf_expense`.create_by = 'u1' AND `wf_expense`.dept_id = 'dept-1'",
                sql);
    }

    @Test
    void compileRecordSqlEscapesQuotesInUsername() {
        SysUser user = user();
        user.setUsername("o'reilly");

        String sql = compiler.compileRecordSql(
                "expense",
                "biz.create_by = #{username}",
                user);

        assertEquals("`wf_expense`.create_by = 'o''reilly'", sql);
    }

    @Test
    void matchesUserWrapsBooleanFragment() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        boolean matched = compiler.matchesUser(
                "#{userId} IN (SELECT user_id FROM special_auditors)",
                user());

        assertTrue(matched);
        verify(jdbcTemplate).queryForObject(
                "SELECT CASE WHEN ('u1' IN (SELECT user_id FROM special_auditors)) THEN 1 ELSE 0 END",
                Integer.class);
    }

    @Test
    void matchesUserFailsClosedWhenQueryFails() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new RuntimeException("bad sql"));

        assertFalse(compiler.matchesUser("#{userId} = 'u1'", user()));
    }

    @Test
    void recordSqlAllowsBizAndRejectsCompleteStatement() {
        compiler.validate("biz.create_by = #{userId}", true);

        IllegalArgumentException complete = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.validate("SELECT * FROM biz", true));
        assertTrue(complete.getMessage().contains("只填写条件片段"));
    }

    @Test
    void audienceSqlRejectsBizAlias() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.validate("biz.create_by = #{userId}", false));
        assertTrue(exception.getMessage().contains("不能引用主表别名 biz"));
    }

    @Test
    void rejectsSemicolonCommentsUnknownPlaceholderAndDml() {
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.validate("1=1; DROP TABLE t", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.validate("1=1 -- comment", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.validate("create_by = #{foo}", true));
        IllegalArgumentException delete = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.validate("DELETE FROM t WHERE 1=1", true));
        assertTrue(delete.getMessage().contains("完整 SQL")
                || delete.getMessage().contains("关键字"));
    }

    @Test
    void rejectsEmptyAndMissingEntityForRecordSql() {
        assertThrows(IllegalArgumentException.class, () -> compiler.validate("  ", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compileRecordSql(null, "biz.id = 1", user()));
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId("u1");
        user.setUsername("alice");
        user.setDeptId("dept-1");
        user.setOrgId("org-1");
        return user;
    }
}
