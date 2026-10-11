package com.workflow.entity.ui;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormPublished;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 执行真实 Mapper SQL，验证覆盖发布、历史隔离及默认选择的事务边界。 */
class CurrentFormPublicationDatabaseTest {
    @Test void overwriteKeepsOneRowAndHistoricalFormCannotBeResolved() throws Exception {
        try (Fixture db = new Fixture()) {
            EntityFormPublished form = publication("first-hash");
            assertEquals(1, db.publications.insert(form));
            assertEquals("first-hash", db.releases.findActive("FORM", "form").getId());
            assertEquals("form", db.releases.selectById("first-hash").getConfigId());
            db.jdbc.update("INSERT INTO ui_config_release(id,config_type,config_id,version,snapshot_document,status) VALUES('old-release','FORM','form',7,'{}','ARCHIVED')");
            assertNull(db.releases.selectById("old-release"));
            assertTrue(db.releases.findReleases("FORM","form").isEmpty());
            form.setContentHash("second-hash"); form.setSnapshotDocument("{\"current\":true}");
            assertEquals(1, db.publications.updateById(form));
            assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM entity_form_published",Integer.class));
            assertNull(db.releases.selectById("first-hash"));
            assertEquals("{\"current\":true}", db.releases.selectById("second-hash").getSnapshotDocument());
            assertEquals(1, db.releases.findActive("FORM","form").getVersion());
            db.jdbc.update("INSERT INTO ui_config_release(id,config_type,config_id,version,snapshot_document,status) VALUES('list-release','LIST','list',3,'{}','ACTIVE')");
            assertEquals(3,db.releases.selectById("list-release").getVersion());
        }
    }
    @Test void currentDefaultAndPublicationRollbackTogether() throws Exception {
        try (Fixture db = new Fixture()) {
            db.publications.insert(publication("first-hash"));
            db.publications.insertDefault("entity","form");
            assertEquals("form",db.releases.findCurrentDefaultForm("entity").getConfigId());
            assertThrows(IllegalStateException.class, () -> db.transactions.execute(status -> {
                db.publications.lockPublication();
                db.publications.clearDefault("entity");
                db.publications.updateById(publication("new-hash"));
                throw new IllegalStateException("validation failed");
            }));
            assertEquals("first-hash",db.releases.findCurrentDefaultForm("entity").getId());
        }
    }
    private EntityFormPublished publication(String hash) {
        var row = new EntityFormPublished(); row.setFormId("form"); row.setEntityId("entity");
        row.setContentHash(hash); row.setSnapshotDocument("{}"); row.setPublishedAt(LocalDateTime.now()); return row;
    }
    private static class Fixture implements AutoCloseable {
        final JdbcTemplate jdbc;
        final UiConfigReleaseMapper releases;
        final EntityFormPublishedMapper publications;
        final TransactionTemplate transactions;
        Fixture() throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:current_form_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE entity_form_published(form_id VARCHAR PRIMARY KEY,entity_id VARCHAR,snapshot_document CLOB,content_hash VARCHAR UNIQUE,published_by VARCHAR,published_at TIMESTAMP)");
            jdbc.execute("CREATE TABLE entity_form_current_default(entity_id VARCHAR PRIMARY KEY,form_id VARCHAR)");
            jdbc.execute("CREATE TABLE entity_form_publication_lock(lock_id INT PRIMARY KEY)"); jdbc.execute("INSERT INTO entity_form_publication_lock VALUES(1)");
            jdbc.execute("CREATE TABLE ui_config_release(id VARCHAR PRIMARY KEY,config_type VARCHAR,config_id VARCHAR,version INT,snapshot_document CLOB,content_hash VARCHAR,status VARCHAR,description VARCHAR,release_mode VARCHAR,base_release_id VARCHAR,risk_level VARCHAR,rollout_scope VARCHAR,patch_document CLOB,override_risk INT,override_reason VARCHAR,published_by VARCHAR,published_at TIMESTAMP)");
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            config.addMapper(UiConfigReleaseMapper.class); config.addMapper(EntityFormPublishedMapper.class);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
            var session = new SqlSessionTemplate(factory.getObject()); releases=session.getMapper(UiConfigReleaseMapper.class); publications=session.getMapper(EntityFormPublishedMapper.class);
            transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
        }
        @Override public void close() { jdbc.execute("SHUTDOWN"); }
    }
}
