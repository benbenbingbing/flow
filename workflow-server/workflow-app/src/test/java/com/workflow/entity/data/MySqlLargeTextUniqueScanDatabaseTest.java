package com.workflow.entity.data;

import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.SchemaDdlExecutor;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.dialect.MySqlSchemaDdlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 发布预检必须比较完整大文本，不能因相同前缀、NULL 或删除历史误报唯一性冲突。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlLargeTextUniqueScanDatabaseTest {
    @Test
    void comparesWholeTextAndKeepsDatabaseCollationDeletionAndNullSemantics() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String table = f.table("biz_text_unique", "id VARCHAR(64) PRIMARY KEY,deleted INT,memo LONGTEXT");
            String prefix = "中文😀".repeat(5000);
            for (String id : List.of("a1", "a2"))
                f.jdbc.update("INSERT INTO " + table + " VALUES (?,0,?)", id, prefix + "same");
            f.jdbc.update("INSERT INTO " + table + " VALUES ('b1',0,?),('a-deleted',1,?)", prefix + "different", prefix + "same");
            f.jdbc.update("INSERT INTO " + table + " VALUES ('c1',0,'Écho'),('c2',0,'echo'),('n1',0,NULL),('n2',0,NULL)");
            List<String> conflicts = scan(f, table);
            assertEquals(List.of("备注=" + prefix + "same（2条）", "备注=Écho（2条）"), conflicts);
        }
    }

    @Test
    void reportsFiveDistinctGroupsWithTheirFullCounts() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String table = f.table("biz_text_samples", "id VARCHAR(64) PRIMARY KEY,deleted INT,memo LONGTEXT");
            String prefix = "shared-prefix-".repeat(500);
            for (int group = 0; group < 6; group++) {
                for (int row = 0; row < group + 2; row++) {
                    f.jdbc.update("INSERT INTO " + table + " VALUES (?,0,?)", group + "-" + row, prefix + group);
                }
            }
            List<String> conflicts = scan(f, table);
            assertEquals(5, conflicts.size());
            for (int group = 0; group < 5; group++)
                assertEquals("备注=" + prefix + group + "（" + (group + 2) + "条）", conflicts.get(group));
        }
    }

    private List<String> scan(MySqlRuntimePaginationDatabaseTest.Fixture f, String table) {
        var entity = new EntityDefinition();
        entity.setId("text-entity"); entity.setEntityCode("text-entity");
        var field = new EntityField();
        field.setFieldCode("notes"); field.setDbColumnName("memo"); field.setFieldName("备注");
        field.setFieldType(EntityField.FieldType.TEXT); field.setIsUnique(true);
        var resolver = mock(EntityPhysicalTableResolver.class);
        when(resolver.resolve(entity)).thenReturn(table);
        var schema = new MySqlSchemaDdlDialect();
        var service = new DynamicTableService(f.jdbc, mock(EntityFieldMapper.class), resolver, mock(SchemaDdlExecutor.class),
                schema, new JdbcSchemaMetadata(f.jdbc, schema), DatabaseQueryDialects.forDatabaseId("MYSQL"));
        return service.scanUniqueConflicts(entity, List.of(field));
    }
}
