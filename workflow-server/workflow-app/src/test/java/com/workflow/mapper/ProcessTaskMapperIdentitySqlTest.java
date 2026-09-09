package com.workflow.mapper;

import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程任务 Mapper 用户身份 SQL 单元测试。
 *
 * <p>验证待办与已办查询 SQL 同时支持用户名与用户 ID 两种身份标识，
 * 待办直接读取引擎身份关系，已办保留本地办理记录的身份回退。</p>
 */
class ProcessTaskMapperIdentitySqlTest {

    /**
     * 待办查询 SQL 应同时接受用户名或用户 ID。
     *
     * <p>列表和统计均将引擎办理人、候选用户与 ID/用户名两个别名匹配。</p>
     */
    @Test
    void todoQueriesAcceptUsernameOrUserId() throws Exception {
        String listSql = selectSql("selectTodoByUser", String.class);
        String countSql = selectSql("countTodoByUser", String.class);

        for (String sql : java.util.List.of(listSql, countSql)) {
            assertTrue(sql.contains("u.username = #{userId} OR u.id = #{userId}"));
            assertTrue(sql.contains("ft.ASSIGNEE_ COLLATE utf8mb4_unicode_ci IN (u.id, u.username)"));
            assertTrue(sql.contains("candidate.USER_ID_ COLLATE utf8mb4_unicode_ci IN (u.id, u.username)"));
        }
    }

    /**
     * 已办查询 SQL 应同时接受用户名或用户 ID。
     *
     * <p>断言 selectDoneByUser 与 countDoneByUser 的 SQL 含双向身份回退子查询。</p>
     */
    @Test
    void doneQueriesAcceptUsernameOrUserId() throws Exception {
        String listSql = selectSql("selectDoneByUser", String.class);
        String countSql = selectSql("countDoneByUser", String.class);

        assertUserIdentityFallbacks(listSql);
        assertUserIdentityFallbacks(countSql);
    }

    /**
     * 断言 SQL 含双向身份回退子查询(用户名查 ID、ID 查用户名)。
     *
     * @param sql 待校验的 SQL 字符串
     */
    private void assertUserIdentityFallbacks(String sql) {
        assertTrue(sql.contains("SELECT id FROM sys_user WHERE username = #{userId}"),
                "query must match tasks assigned to a user id when called with username");
        assertTrue(sql.contains("SELECT username FROM sys_user WHERE id = #{userId}"),
                "query must match tasks assigned to a username when called with user id");
    }

    /**
     * 反射读取 Mapper 方法的 @Select 注解并拼接为完整 SQL。
     *
     * @param methodName 方法名
     * @param parameterTypes 参数类型列表
     * @return 拼接后的 SQL 字符串
     */
    private String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProcessTaskMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        return String.join("", select.value());
    }
}
