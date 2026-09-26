package com.workflow.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.workflow.core.database.mybatis.OffsetPage;
import com.workflow.entity.data.application.model.EntityExportBatch;
import com.workflow.entity.data.application.EntityQueryConditions;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** H2 兼容模式执行真实 Mapper/分页/类型绑定；覆盖复合游标、NULL 顺序与条件求交。 */
class EntityExportBatchDatabaseTest {
    @ParameterizedTest
    @CsvSource({"MYSQL,MySQL,LOW,ASC", "MYSQL,MySQL,LOW,DESC",
            "POSTGRESQL,PostgreSQL,HIGH,ASC", "POSTGRESQL,PostgreSQL,HIGH,DESC"})
    void batchesMatchDatabaseOrderWithoutDuplicatesAndRetainAllRestrictions(
            String vendor, String mode, String nullOrder, String direction) {
        var db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("export_" + java.util.UUID.randomUUID() + ";MODE=" + mode
                        + ";DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=" + nullOrder).build();
        try {
            var jdbc = new JdbcTemplate(db);
            jdbc.execute("CREATE TABLE entity_export (id VARCHAR(64) PRIMARY KEY, amount DECIMAL(12,2), "
                    + "status VARCHAR(16), create_by VARCHAR(64), deleted INT, create_time TIMESTAMP)");
            for (int i = 0; i < 705; i++) {
                jdbc.update("INSERT INTO entity_export VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", id(i),
                        i % 8 == 0 ? null : BigDecimal.valueOf(i % 7), i % 9 == 0 ? "CLOSED" : "OPEN",
                        i % 11 == 0 ? "other" : "allowed", i % 13 == 0 ? 1 : 0);
            }
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), db));
            configuration.setDatabaseId(vendor);
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(
                    "MYSQL".equals(vendor) ? DbType.MYSQL : DbType.POSTGRE_SQL));
            configuration.addInterceptor(interceptor);
            configuration.addMapper(EntityDataDynamicMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);
            var amount = new EntityField();
            amount.setFieldCode("amount"); amount.setDbColumnName("amount"); amount.setFieldType(EntityField.FieldType.DECIMAL);
            var condition = EntityQueryConditions.fromPublishedFields(
                    Map.of("status", "OPEN", "status_op", "EQ", "id_start", id(20)), List.of(amount));
            // 超过单个 IN 分组大小，且含 SQL 标记的 ID 必须仍作为值绑定。
            var ids = new ArrayList<>(IntStream.range(0, 650).mapToObj(EntityExportBatchDatabaseTest::id).toList());
            ids.add("' OR 1=1 --");
            var expected = jdbc.queryForList("SELECT id FROM entity_export WHERE deleted = 0 AND status = 'OPEN'"
                    + " AND create_by = 'allowed' AND id >= '0020' AND id < '0650' ORDER BY amount "
                    + direction + ", id DESC", String.class);
            try (var session = factory.openSession()) {
                var mapper = session.getMapper(EntityDataDynamicMapper.class);
                List<String> actual = new ArrayList<>();
                EntityExportBatch.Cursor cursor = null;
                for (int batch = 0; batch < 20; batch++) {
                    var rows = mapper.selectExportBatch(new OffsetPage<>(0, 37), "entity_export", condition,
                            "create_by = #{permissionParameters.owner}", Map.of("owner", "allowed"),
                            ids, cursor, "amount", direction);
                    assertTrue(rows.size() <= 37, "数据库必须限制每次返回的行数");
                    if (rows.isEmpty()) break;
                    rows.forEach(row -> actual.add(row.get("id").toString()));
                    var last = rows.get(rows.size() - 1);
                    cursor = new EntityExportBatch.Cursor(last.get("amount"), last.get("id").toString());
                }
                assertEquals(expected, actual);
                assertEquals(actual.size(), new java.util.HashSet<>(actual).size());
                assertTrue(mapper.selectExportBatch(new OffsetPage<>(0, 37), "entity_export", condition,
                        null, Map.of(), List.of(), null, "amount", direction).isEmpty());
            }
        } finally {
            db.shutdown();
        }
    }

    private static String id(int value) { return String.format("%04d", value); }
}
