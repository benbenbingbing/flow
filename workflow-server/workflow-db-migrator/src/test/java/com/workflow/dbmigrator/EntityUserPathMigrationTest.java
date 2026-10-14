package com.workflow.dbmigrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 检查增量迁移与程序描述的参数协议一致，避免数据库目录仍拒绝 V2。 */
class EntityUserPathMigrationTest {
    @Test void newMigrationPreservesHistoricalCreatorsAndAcceptsBothContracts() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/V119__entity_user_relation_path.sql");
        String sql = Files.readString(path);
        assertTrue(sql.contains("ADD COLUMN `create_by` varchar(64) DEFAULT NULL"));
        assertFalse(sql.contains("UPDATE `sys_organization`"), "不能伪造历史部门创建人");
        String schema = sql.substring(sql.indexOf("'{\"oneOf\"" ) + 1, sql.indexOf("',", sql.indexOf("'{\"oneOf\"")));
        var json = new ObjectMapper();
        var stored = json.readTree(schema);
        assertEquals(1, stored.path("oneOf").get(0).path("properties").path("schemaVersion").path("const").asInt());
        assertEquals(2, stored.path("oneOf").get(1).path("properties").path("schemaVersion").path("const").asInt());
        String source = Files.readString(Path.of("../workflow-process/src/main/java/com/workflow/process/assignment/extension/EntityUserPathSchema.java"));
        String descriptor = source.substring(source.indexOf("{\"oneOf\""), source.lastIndexOf("\"\"\"" )).trim();
        assertEquals(stored, json.readTree(descriptor));
    }
}
