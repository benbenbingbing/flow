package com.workflow.entity.data;

import com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniqueConflictMapper;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.support.BusinessMapperTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 无外部数据库时也执行真实 Mapper，验证 LOB 结果和计数未因迁移被截断或丢失。 */
class EntityUniqueConflictMapperDatabaseTest {
    @Test
    void returnsWholeTextAndCountsOnlyLiveDuplicatesWithinFiveGroups() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE sample_conflicts(id VARCHAR(64) PRIMARY KEY, deleted INT, memo CLOB)");
            String prefix = "中文😀".repeat(5000);
            for (int group = 0; group < 6; group++) {
                for (int row = 0; row < group + 2; row++) {
                    jdbc.update("INSERT INTO sample_conflicts VALUES (?,0,?)", group + "-" + row, prefix + group);
                }
            }
            jdbc.update("INSERT INTO sample_conflicts VALUES ('deleted',1,?)", prefix + "0");
            jdbc.update("INSERT INTO sample_conflicts VALUES ('unique',0,?)", prefix + "unique");
            jdbc.update("INSERT INTO sample_conflicts VALUES ('null1',0,NULL),('null2',0,NULL)");

            var mapper = BusinessMapperTestSupport.mapper(database, EntityUniqueConflictMapper.class);
            var rows = mapper.scan("sample_conflicts", "memo", SchemaType.Kind.LARGE_TEXT,
                    DatabaseQueryDialects.forDatabaseId("MYSQL"));
            assertEquals(5, rows.size());
            for (int group = 0; group < rows.size(); group++) {
                assertEquals(prefix + group, rows.get(group).getValueText());
                assertEquals(group + 2L, rows.get(group).getDuplicateCount());
            }
        } finally {
            database.shutdown();
        }
    }
}
