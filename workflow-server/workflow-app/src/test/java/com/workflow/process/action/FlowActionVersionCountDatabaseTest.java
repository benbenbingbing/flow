package com.workflow.process.action;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.process.action.application.FlowActionDesignAdapter;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 真实执行聚合 SQL，验证版本隔离、动作过滤及不同大小写方言下的查询结果映射。 */
class FlowActionVersionCountDatabaseTest {
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        if (jdbc != null) {
            jdbc.execute("SHUTDOWN");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL", "Oracle"})
    void countsPublishedSnapshotsIncludingDisabledActions(String mode) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:action_counts_" + UUID.randomUUID()
                + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE process_action (
                    id VARCHAR(64) PRIMARY KEY, version_id VARCHAR(64),
                    status VARCHAR(16), deleted INT, enabled BOOLEAN, scope_type VARCHAR(32)
                )
                """);
        action("global", "current", "PUBLISHED", 0, true, "PROCESS");
        action("node", "current", "PUBLISHED", 0, true, "NODE");
        action("disabled", "current", "PUBLISHED", 0, false, "SEQUENCE_FLOW");
        action("deleted", "current", "PUBLISHED", 1, true, "NODE");
        action("draft", "current", "DRAFT", 0, true, "NODE");
        action("unpublished", null, "DRAFT", 0, true, "PROCESS");
        action("old", "previous", "PUBLISHED", 0, true, "PROCESS");
        action("another", "other-process", "PUBLISHED", 0, true, "PROCESS");

        var config = new MybatisConfiguration();
        config.addMapper(FlowActionMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(FlowActionMapper.class);
        var adapter = new FlowActionDesignAdapter(null, null, null, mapper);

        assertEquals(Map.of("current", 3L, "previous", 1L),
                adapter.countPublishedActionsByVersionIds(List.of("current", "previous", "empty")));
        assertEquals(Map.of(), adapter.countPublishedActionsByVersionIds(List.of("empty")));
    }

    /** 空版本列表不生成 IN ()，也不能退化为统计整个动作表。 */
    @Test
    void skipsCountQueryWithoutVersions() {
        var mapper = mock(FlowActionMapper.class);
        var adapter = new FlowActionDesignAdapter(null, null, null, mapper);

        assertEquals(Map.of(), adapter.countPublishedActionsByVersionIds(List.of()));
        verifyNoInteractions(mapper);
    }

    private void action(String id, String version, String status, int deleted, boolean enabled, String scope) {
        jdbc.update("INSERT INTO process_action VALUES (?, ?, ?, ?, ?, ?)",
                id, version, status, deleted, enabled, scope);
    }
}
