package com.workflow.database;

import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.dbmigrator.schema.SchemaDdlReplayVerifier;
import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.api.schema.SchemaDefault;
import com.workflow.integration.database.api.schema.SchemaTable;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.integration.database.schema.dialect.OceanBaseMySqlSchemaDdlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** OB 实库回归：不依赖 Connector/J 的可选元数据配置，表注释仍须参与 DDL 重放验收。 */
@EnabledIfEnvironmentVariable(named = "FLOW_OBMYSQL_TEST_URL", matches = ".+")
class OceanBaseSchemaMetadataDatabaseTest {
    @Test
    void tableCommentsAndDdlReplayWorkWithoutDriverInformationSchemaOption() {
        String url = System.getenv("FLOW_OBMYSQL_TEST_URL");
        // 显式关闭驱动的替代路径，确保验证的是应用修复，而非环境 URL 掩盖的问题。
        url = url.replaceAll("(?i)([?&])useInformationSchema=[^&]*", "$1useInformationSchema=false");
        if (!url.toLowerCase(java.util.Locale.ROOT).contains("useinformationschema=")) {
            url += (url.contains("?") ? "&" : "?") + "useInformationSchema=false";
        }
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url,
                System.getenv("FLOW_OBMYSQL_TEST_USER"), System.getenv("FLOW_OBMYSQL_TEST_PASSWORD")));
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).contains("OceanBase"));
        var dialect = new OceanBaseMySqlSchemaDdlDialect();
        String table = "biz_ob_metadata_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String comment = "业务数据参与团队事件 O'Reilly\\中文";
        var definition = new SchemaTable(table, List.of(new SchemaColumn("id", SchemaType.string(64),
                false, SchemaDefault.none(), "记录标识", false)), List.of("id"), List.of(), comment, false);
        String ddl = dialect.createTable(definition).get(0);
        try {
            jdbc.execute(ddl);
            var metadata = new JdbcSchemaMetadata(jdbc, dialect);
            assertEquals(comment, metadata.tables().stream().filter(item -> item.name().equals(table))
                    .findFirst().orElseThrow().comment());
            var verifier = new SchemaDdlReplayVerifier(jdbc, dialect);
            assertTrue(verifier.isApplied(ddl), "创建成功的表不能因为驱动丢失注释而被判定为未应用");
            jdbc.execute("ALTER TABLE `" + table + "` COMMENT='不同的注释'");
            assertFalse(verifier.isApplied(ddl), "修复仍必须发现真正的注释差异");
            jdbc.execute("ALTER TABLE `" + table + "` COMMENT=''");
            assertEquals("", metadata.tables().stream().filter(item -> item.name().equals(table))
                    .findFirst().orElseThrow().comment());
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
    }
}
