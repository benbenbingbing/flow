package com.workflow.mapper;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import java.util.List;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDisplayNameBatchDatabaseTest {
    @Test
    void groupLookupRetainsRequestedAliasesAndExcludesDeletedGroups() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE sys_group(id VARCHAR(64), group_code VARCHAR_IGNORECASE(64), group_name VARCHAR(64), deleted INT)");
            jdbc.update("INSERT INTO sys_group VALUES ('g1','reviewers','审批组',0), ('g2','deleted','已删除',1)");
            var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), database));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
                var rows = session.getMapper(com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper.class)
                        .selectDisplayGroupsByCodes(List.of("REVIEWERS", "reviewers", "deleted", "missing"));
                assertEquals(List.of("REVIEWERS", "reviewers"), rows.stream().map(row -> row.getLookupCode()).toList());
                assertTrue(rows.stream().allMatch(row -> "g1".equals(row.getId()) && "审批组".equals(row.getGroupName())));
            }
        } finally { database.shutdown(); }
    }

    @Test
    void preservesDatabaseCollationUsernamePriorityAndDeletedUserFallback() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE sys_user(id VARCHAR(64), username VARCHAR_IGNORECASE(64), nickname VARCHAR(64), deleted INT)");
            jdbc.update("INSERT INTO sys_user VALUES ('u1','alice','爱丽丝',0), ('alice','bob','鲍勃',0), ('gone','deleted','已删除',1)");
            var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), database));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(SysUserMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
                var mapper = mock(SysUserMapper.class, org.mockito.AdditionalAnswers.delegatesTo(session.getMapper(SysUserMapper.class)));
                var service = new SysUserService(mapper, null, null, null, null, null, null);
                var names = service.getDisplayNameMap(List.of("alice", "ALICE", "u1", "bob", "gone", "x' OR 1=1 --", "alice"));
                assertEquals("爱丽丝(alice)", names.get("alice"), "用户名优先于另一个人的同名 ID");
                assertEquals("爱丽丝(alice)", names.get("ALICE"), "数据库大小写比较规则不能被 Java Map 改变");
                assertEquals("爱丽丝(alice)", names.get("u1"));
                assertEquals("鲍勃(bob)", names.get("bob"));
                assertEquals("gone", names.get("gone"));
                assertEquals("x' OR 1=1 --", names.get("x' OR 1=1 --"));
                verify(mapper).selectDisplayNameRows(org.mockito.ArgumentMatchers.argThat(keys -> keys.size() == 6));
                clearInvocations(mapper);
                var many = java.util.stream.IntStream.range(0, 205).mapToObj(i -> "missing-" + i).toList();
                assertEquals(205, service.getDisplayNameMap(many).size());
                verify(mapper, times(3)).selectDisplayNameRows(org.mockito.ArgumentMatchers.argThat(keys -> keys.size() <= 100));
            }
        } finally { database.shutdown(); }
    }
}
