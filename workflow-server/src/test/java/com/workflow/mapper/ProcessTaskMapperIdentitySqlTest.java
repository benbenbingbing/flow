package com.workflow.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessTaskMapperIdentitySqlTest {

    @Test
    void todoQueriesAcceptUsernameOrUserId() throws Exception {
        String listSql = selectSql("selectTodoByUser", String.class);
        String countSql = selectSql("countTodoByUser", String.class);

        assertUserIdentityFallbacks(listSql);
        assertUserIdentityFallbacks(countSql);
        assertTrue(listSql.contains("u.username = #{userId} OR u.id = #{userId}"));
        assertTrue(countSql.contains("u.username = #{userId} OR u.id = #{userId}"));
    }

    @Test
    void doneQueriesAcceptUsernameOrUserId() throws Exception {
        String listSql = selectSql("selectDoneByUser", String.class);
        String countSql = selectSql("countDoneByUser", String.class);

        assertUserIdentityFallbacks(listSql);
        assertUserIdentityFallbacks(countSql);
    }

    private void assertUserIdentityFallbacks(String sql) {
        assertTrue(sql.contains("SELECT id FROM sys_user WHERE username = #{userId}"),
                "query must match tasks assigned to a user id when called with username");
        assertTrue(sql.contains("SELECT username FROM sys_user WHERE id = #{userId}"),
                "query must match tasks assigned to a username when called with user id");
    }

    private String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProcessTaskMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        return String.join("", select.value());
    }
}
