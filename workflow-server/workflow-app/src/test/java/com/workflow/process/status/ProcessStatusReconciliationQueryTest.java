package com.workflow.process.status;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 执行真实对账查询及分页，验证失败事件不会挤占缺失事件的补偿批次。 */
class ProcessStatusReconciliationQueryTest {
    private JdbcTemplate jdbc;
    private EntityProcessLinkMapper links;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:reconciliation_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE entity_process_link (id VARCHAR(64) PRIMARY KEY, process_instance_id VARCHAR(64), state VARCHAR(16), update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE ACT_HI_PROCINST (PROC_INST_ID_ VARCHAR(64) PRIMARY KEY, END_TIME_ TIMESTAMP)");
        jdbc.execute("CREATE TABLE workflow_outbox_event (topic VARCHAR(64), aggregate_type VARCHAR(64), aggregate_id VARCHAR(64), event_key VARCHAR(128), status VARCHAR(16))");
        var config = new MybatisConfiguration();
        config.setDatabaseId("MYSQL");
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(EntityProcessLinkMapper.class);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(pagination);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        links = new SqlSessionTemplate(factory.getObject()).getMapper(EntityProcessLinkMapper.class);
    }

    @AfterEach
    void cleanup() {
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void existingEventsOfEveryStateAreExcludedBeforeBatchLimit() {
        for (String state : List.of("PENDING", "PROCESSING", "FAILED", "DEAD", "PROCESSED")) {
            link(state);
            event(state, "PROCESS_STATUS_SYNC", "PROCESS_END:END", state);
        }
        link("missing");
        // 缺失事件排在失败事件之后，仍必须进入 batchSize=1 的补偿批次。
        jdbc.update("UPDATE entity_process_link SET update_time='2026-10-15 00:00:00' WHERE id='missing'");
        assertEquals(List.of("missing"), ids(1));
    }

    @Test
    void taskEventsAndOtherTopicsDoNotHideMissingEndEvents() {
        link("task-only");
        event("task-only", "PROCESS_STATUS_SYNC", "TASK_COMPLETED:task", "PROCESSED");
        link("other-topic");
        event("other-topic", "OTHER_TOPIC", "PROCESS_END:END", "DEAD");
        link("running");
        jdbc.update("UPDATE ACT_HI_PROCINST SET END_TIME_=NULL WHERE PROC_INST_ID_='running'");
        link("closed");
        jdbc.update("UPDATE entity_process_link SET state='ENDED' WHERE id='closed'");
        assertEquals(List.of("other-topic", "task-only"), ids(10).stream().sorted().toList());
    }

    private List<String> ids(int limit) {
        return links.findEndedActiveForReconciliation(limit).stream().map(row -> row.getId()).toList();
    }

    private void link(String id) {
        jdbc.update("INSERT INTO entity_process_link VALUES (?,?,'ACTIVE','2026-10-01 00:00:00')", id, id);
        jdbc.update("INSERT INTO ACT_HI_PROCINST VALUES (?,'2026-10-01 00:00:00')", id);
    }

    private void event(String process, String topic, String suffix, String state) {
        jdbc.update("INSERT INTO workflow_outbox_event VALUES (?,'PROCESS_INSTANCE',?,?,?)",
                topic, process, process + ":" + suffix, state);
    }
}
