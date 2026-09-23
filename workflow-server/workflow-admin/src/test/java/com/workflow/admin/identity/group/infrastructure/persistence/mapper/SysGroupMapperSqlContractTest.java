package com.workflow.admin.identity.group.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
        assertFalse(codeSql.contains("deleted = 0"));
        // 成员校验只投影 ID；由 MP 统一排除删除行，不能顺带过滤禁用用户。
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteField("deleted")));
        configuration.addMapper(SysUserMapper.class);
        SysUserMapper mapper = mock(SysUserMapper.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            Wrapper<SysUser> wrapper = invocation.getArgument(0);
            String userSql = configuration.getMappedStatement(SysUserMapper.class.getName() + ".selectObjs")
                    .getBoundSql(Map.of("ew", wrapper)).getSql().toLowerCase();
            assertTrue(userSql.contains("deleted=0"));
            assertTrue(userSql.contains("id in"));
            assertFalse(userSql.contains("status"));
            return List.of("user-1");
        }).when(mapper).selectObjs(any());
        mapper.selectExistingIdsByIds(List.of("user-1"));
        assertTrue(mapper.selectExistingIdsByIds(List.of()).isEmpty());
        verify(mapper, times(1)).selectObjs(any());
    }

    private static String selectSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", Arrays.asList(select.value()));
    }
}
