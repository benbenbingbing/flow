package com.workflow.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程任务 Mapper 用户身份 SQL 单元测试。
 *
 * <p>验证待办与已办查询 SQL 同时支持用户名与用户 ID 两种身份标识，
 * 通过子查询回退确保传入用户名时能匹配到对应 ID 的任务。</p>
 */
class ProcessTaskMapperIdentitySqlTest {

    /**
     * 待办查询 SQL 应同时接受用户名或用户 ID。
     *
     * <p>断言 selectTodoByUser 与 countTodoByUser 的 SQL 含双向身份回退子查询
     * 与 OR 条件。</p>
     */
    @Test
    void todoQueriesAcceptUsernameOrUserId() throws Exception {
        String listSql = selectSql("selectTodoByUser", String.class);
        String countSql = selectSql("countTodoByUser", String.class);

        assertUserIdentityFallbacks(listSql);
        assertUserIdentityFallbacks(countSql);
        assertTrue(listSql.contains("u.username = #{userId} OR u.id = #{userId}"));
        assertTrue(countSql.contains("u.username = #{userId} OR u.id = #{userId}"));
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
