package com.workflow.admin.identity.group.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 用户组 Mapper SQL 契约测试，区分管理回显与流程运行时的成员状态语义。
 */
class SysGroupMapperSqlContractTest {

    @Test
    void runtimeMemberQueryOnlyReturnsEnabledUsers() throws Exception {
        String sql = selectSql(
                SysGroupMapper.class.getMethod("selectGroupUsers", String.class));

        assertTrue(sql.contains("u.deleted = 0"));
        assertTrue(sql.contains("u.status = '0'"));
    }

    @Test
    void managementMemberQueriesKeepDisabledButNotDeletedUsers() throws Exception {
        String detailSql = selectSql(
                SysGroupMapper.class.getMethod("selectGroupMembers", String.class));
        String listSql = selectSql(SysGroupMapper.class.getMethod(
                "selectGroupUserIdsByGroupIds", List.class));

        assertTrue(detailSql.contains("u.deleted = 0"));
        assertFalse(detailSql.contains("u.status"));
        assertTrue(listSql.contains("u.deleted = 0"));
        assertFalse(listSql.contains("u.status"));
    }

    @Test
    void codeUniquenessIncludesDeletedRowsAndMemberValidationAllowsDisabledUsers()
            throws Exception {
        String codeSql = selectSql(SysGroupMapper.class.getMethod(
                "existsGroupCode", String.class, String.class));
        String userSql = selectSql(SysUserMapper.class.getMethod(
                "selectExistingIdsByIds", List.class));

        assertFalse(codeSql.contains("deleted = 0"));
        assertTrue(userSql.contains("deleted = 0"));
        assertFalse(userSql.contains("status"));
    }

    private static String selectSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", Arrays.asList(select.value()));
    }
}
