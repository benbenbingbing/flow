package com.workflow.database;

import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.core.database.InitializedDriverDataSource;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 MySQL 执行分页及 count，防止装配迁移后插件失效或偏移参数顺序改变。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlDatabasePaginationTest {
    @Test
    void configuredInterceptorPreservesPageRowsTotalAndOrdering() {
        var source = new InitializedDriverDataSource(System.getenv("FLOW_MYSQL_TEST_URL"), System.getenv("FLOW_MYSQL_TEST_USER"),
                System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null, DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
        var jdbc = new JdbcTemplate(source);
        String table = "biz_page_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbc.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY)");
        try {
            for (int id = 1; id <= 5; id++) jdbc.update("INSERT INTO " + table + " (id) VALUES (?)", id);
            var config = new Configuration(new Environment("mysql", new JdbcTransactionFactory(), source));
            config.addInterceptor(new DatabaseMybatisConfiguration().mybatisPlusInterceptor(new MySqlSchemaDdlDialect()));
            config.addMapper(PageMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
                var mapper = session.getMapper(PageMapper.class);
                var second = new Page<Integer>(2, 2);
                assertEquals(List.of(3, 4), mapper.page(second, table));
                assertEquals(5, second.getTotal());
                assertEquals(3, second.getPages());
                var last = new Page<Integer>(3, 2);
                assertEquals(List.of(5), mapper.page(last, table));
                assertEquals(5, last.getTotal());
            }
        } finally { jdbc.execute("DROP TABLE IF EXISTS " + table); }
    }

    interface PageMapper {
        @Select("SELECT id FROM ${table} ORDER BY id")
        List<Integer> page(Page<Integer> page, @Param("table") String table);
    }
}
