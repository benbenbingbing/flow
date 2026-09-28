package com.workflow.admin.identity.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 MyBatis 实际生成的查询不遗漏失效来源，也不把失效用户提供为接收人。 */
class SysUserHandoverMapperSqlContractTest {
    private final MybatisConfiguration configuration = configuration();

    @Test
    void sourceSearchIncludesDisabledAndDeletedUsersButOnlySelectsIdentityColumns() {
        String sql = sql("selectHandoverUsers", Map.of("keyword", "张三", "targetOnly", false));
        assertTrue(sql.startsWith("select id, username, nickname, status, deleted from sys_user"));
        assertFalse(sql.contains("deleted = 0"));
        assertFalse(sql.contains("status = '0'"));
        assertTrue(sql.contains("id like ? or username like ? or nickname like ?"));
        assertTrue(sql.endsWith("order by username, id"));
        assertFalse(sql.contains("password"));
        assertFalse(sql.contains("token"));
    }

    @Test
    void targetSearchRequiresEnabledAndNotDeletedEvenWithoutKeyword() {
        String sql = sql("selectHandoverUsers", Map.of("keyword", "", "targetOnly", true));
        assertTrue(sql.contains("where deleted = 0 and status = '0'"));
        assertFalse(sql.contains("like"));
    }

    @Test
    void exactIdentityLookupAndLockDoNotFallBackToUsernameOrFilterSourceState() {
        String readSql = sql("selectHandoverUser", Map.of("id", "user-id"));
        assertTrue(readSql.endsWith("where id = ?"));
        assertEquals(readSql + " for update", sql("selectHandoverUserForUpdate", Map.of("id", "user-id")));
    }

    private String sql(String method, Map<String, Object> arguments) {
        return configuration.getMappedStatement(SysUserMapper.class.getName() + "." + method)
                .getBoundSql(arguments).getSql().toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static MybatisConfiguration configuration() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteField("deleted")));
        configuration.addMapper(SysUserMapper.class);
        return configuration;
    }
}
