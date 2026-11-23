package com.workflow.admin.identity.group.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.*;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.port.system.identity.model.GroupMemberFilter;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实成员/标签 SQL 与事务验证，覆盖筛选、改名、离组清理及非法条件的原子拒绝。 */
class SysGroupTagIntegrationTest {
    JdbcTemplate jdbc;
    SysGroupTagService service;
    SysGroupService groups;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:group_tags_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_group(id VARCHAR(64) PRIMARY KEY,group_code VARCHAR(50),group_name VARCHAR(50),description VARCHAR(200),sort_order INT,status CHAR(1),deleted INT DEFAULT 0,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(64) PRIMARY KEY,username VARCHAR(50),nickname VARCHAR(50),status CHAR(1),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_user_group(id VARCHAR(64) PRIMARY KEY,user_id VARCHAR(64),group_id VARCHAR(64),create_time TIMESTAMP,UNIQUE(user_id,group_id))");
        String migration = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration/V136__group_member_tags.sql"));
        for (String statement : migration.substring(0, migration.indexOf("-- 受控目录")).split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        jdbc.update("INSERT INTO sys_group(id,group_code,group_name,status) VALUES('g1','TECH','技术组','0'),('g2','FIN','财务组','0')");
        jdbc.update("INSERT INTO sys_user(id,username,nickname,status) VALUES('u1','alice','张三','0'),('u2','bob',NULL,'0'),('u3','disabled','停用用户','1'),('u4','carol','张三','0')");
        jdbc.update("INSERT INTO sys_user_group(id,user_id,group_id) VALUES('m1','u1','g1'),('m2','u2','g1'),('m3','u3','g1'),('m4','u4','g2'),('m5','u1','g2')");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (Class<?> type : List.of(SysGroupMapper.class, SysUserGroupMapper.class, SysGroupTagMapper.class, SysUserMapper.class)) config.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        groups = new SysGroupService(session.getMapper(SysGroupMapper.class), session.getMapper(SysUserGroupMapper.class), session.getMapper(SysUserMapper.class));
        var raw = new SysGroupTagService(session.getMapper(SysGroupMapper.class), session.getMapper(SysUserGroupMapper.class), session.getMapper(SysGroupTagMapper.class), groups);
        var proxy = new ProxyFactory(raw); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        service = (SysGroupTagService) proxy.getProxy();
    }
    @AfterEach void shutdown() { jdbc.execute("SHUTDOWN"); }

    @Test void emptyTagsMeanWholeGroupAndMultipleTagsUseUnionWithEffectiveUsersOnly() {
        String backend = service.save("g1", null, "后端").getId(), security = service.save("g1", null, "安全").getId();
        service.saveMembers("g1", request(List.of("u1","u2","u3"), row("u1",backend,security),row("u2",backend),row("u3",security)));
        assertEquals(List.of("alice","bob"), service.resolveMembersByTags(List.of(filter("TECH"))));
        assertEquals(List.of("alice"), service.resolveMembersByTags(List.of(filter("TECH",security))));
        assertEquals(List.of("alice","bob"), service.resolveMembersByTags(List.of(filter("TECH",backend,security))));
        assertEquals(List.of("alice","carol"), service.resolveMembersByTags(List.of(filter("TECH",security),filter("FIN"))));
        // 同名用户不能合并；跨组重复成员只展示一次，缺少昵称仍保留可辨认的登录名。
        assertEquals(List.of(new SysGroupTagService.MemberPreview("alice", "张三"),
                new SysGroupTagService.MemberPreview("bob", null),
                new SysGroupTagService.MemberPreview("carol", "张三")),
                service.preview(List.of(filter("TECH",backend,security),filter("FIN"))));
        assertEquals(List.of(new SysGroupTagService.MemberPreview("alice", "张三")),
                service.preview(List.of(filter("TECH",security))));
    }

    @Test void renameKeepsIdAndOldMemberApiPreservesBindingsUntilMemberLeaves() {
        String tag = service.save("g1",null,"安全").getId();
        service.saveMembers("g1", request(List.of("u1","u2"),row("u1",tag)));
        service.save("g1",tag,"信息安全");
        groups.saveGroupUsers("g1",List.of("u1","u2"));
        assertEquals("m1",jdbc.queryForObject("SELECT id FROM sys_user_group WHERE user_id='u1' AND group_id='g1'",String.class));
        assertEquals(List.of("alice"),service.resolveMembersByTags(List.of(filter("TECH",tag))));
        groups.saveGroupUsers("g1",List.of("u2"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_group_tag",Integer.class));
        groups.saveGroupUsers("g1",List.of("u1","u2"));
        assertTrue(service.resolveMembersByTags(List.of(filter("TECH",tag))).isEmpty());
    }

    @Test void crossGroupTagRollsBackMembershipAndDeletedTagNeverExpandsToWholeGroup() {
        String foreign = service.save("g2",null,"复核").getId();
        assertThrows(IllegalArgumentException.class,()->service.saveMembers("g1",request(List.of("u1"),row("u1",foreign))));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_group WHERE group_id='g1'",Integer.class));
        String tag = service.save("g1",null,"后端").getId();
        service.saveMembers("g1",request(List.of("u1","u2"),row("u1",tag)));
        service.delete("g1",tag);
        assertThrows(IllegalArgumentException.class,()->service.resolveMembersByTags(List.of(filter("TECH",tag))));
        assertThrows(IllegalArgumentException.class,()->service.resolveMembersByTags(List.of(filter("TECH",foreign))));
    }

    @Test void rejectsDuplicateNamesAndDisabledGroupsAndCleansGroupChildren() {
        String tag = service.save("g1",null,"后端").getId();
        assertThrows(IllegalArgumentException.class,()->service.save("g1",null," 后端 "));
        service.saveMembers("g1",request(List.of("u1"),row("u1",tag)));
        jdbc.update("UPDATE sys_group SET status='1' WHERE id='g1'");
        assertThrows(IllegalArgumentException.class,()->service.resolveMembersByTags(List.of(filter("TECH",tag))));
        groups.deleteGroup("g1");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_group_tag",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_group_tag",Integer.class));
    }
    private GroupMemberFilter filter(String group, String... tags) { return new GroupMemberFilter(group,List.of(tags)); }
    private SysGroupTagService.MemberTags row(String user,String... tags) { return new SysGroupTagService.MemberTags(user,List.of(tags)); }
    private SysGroupTagService.MembersRequest request(List<String> users,SysGroupTagService.MemberTags... rows) { return new SysGroupTagService.MembersRequest(users,List.of(rows)); }
}
