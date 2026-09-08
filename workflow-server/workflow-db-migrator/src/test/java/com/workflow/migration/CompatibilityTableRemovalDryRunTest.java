package com.workflow.migration;

import db.migration.V080__remove_compatibility_configuration_tables;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认只读预检；显式启用 apply.rollback 时验证实际 DML 并回滚。
 * 两种模式均截获 DROP，绝不执行隐式提交的 DDL。
 */
@EnabledIfSystemProperty(named="flow.compat.dry.url", matches="jdbc:mysql:.*")
class CompatibilityTableRemovalDryRunTest {
    @Test
    void existingConfigurationCanBeParsedAndConvertedWithoutWriting() throws Exception {
        AtomicInteger plannedWrites = new AtomicInteger();
        AtomicInteger plannedDrops = new AtomicInteger();
        try (Connection real = DriverManager.getConnection(System.getProperty("flow.compat.dry.url"),
                System.getenv().getOrDefault("DB_USERNAME", "workflow"),
                System.getenv().getOrDefault("DB_PASSWORD", ""))) {
            boolean applyRollback = Boolean.getBoolean("flow.compat.apply.rollback");
            real.setReadOnly(!applyRollback);
            real.setAutoCommit(false);
            String before = fingerprint(real);
            try {
            Connection guarded = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("createStatement")) {
                            Statement read = real.createStatement();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
                                    (p, m, a) -> {
                                        if (m.getName().equals("executeQuery")) {
                                            assertTrue(((String)a[0]).stripLeading().startsWith("SELECT "));
                                            return read.executeQuery((String)a[0]);
                                        }
                                        if (m.getName().equals("execute")) {
                                            assertEquals("DROP TABLE entity_version_step, entity_change_target_binding, entity_version_scenario, entity_form_field", a[0]);
                                            plannedDrops.incrementAndGet();
                                            return false;
                                        }
                                        if (m.getName().equals("close")) { read.close(); return null; }
                                        throw new AssertionError("不允许执行未声明的 JDBC 操作: " + m.getName());
                                    });
                        }
                        if (method.getName().equals("prepareStatement")) {
                            assertTrue(((String)args[0]).startsWith("INSERT ") || ((String)args[0]).startsWith("UPDATE "));
                            PreparedStatement write = applyRollback ? real.prepareStatement((String) args[0]) : null;
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                    (p,m,a) -> {
                                        if (m.getName().equals("setObject")) {
                                            if (write != null) write.setObject((Integer)a[0], a[1]);
                                            return null;
                                        }
                                        if (m.getName().equals("close")) { if (write != null) write.close(); return null; }
                                        if (m.getName().equals("executeUpdate")) {
                                            plannedWrites.incrementAndGet();
                                            return write == null ? 1 : write.executeUpdate();
                                        }
                                        throw new AssertionError("不允许执行未声明的预检写入: " + m.getName());
                                    });
                        }
                        throw new AssertionError("不允许执行未声明的连接操作: " + method.getName());
                    });
            Context context = (Context) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Context.class},
                    (p,m,a) -> {
                        if (m.getName().equals("getConnection")) return guarded;
                        throw new AssertionError("预检上下文仅提供只读连接");
                    });
            new V080__remove_compatibility_configuration_tables().migrate(context);
            assertEquals(1, plannedDrops.get());
            try (Statement statement=real.createStatement(); ResultSet rs=statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('entity_form_field','entity_version_scenario','entity_version_step','entity_change_target_binding')")) {
                assertTrue(rs.next()); assertEquals(4, rs.getInt(1));
            }
            } finally {
                // 即使任何写入或校验失败，也由本连接显式回滚；不允许迁移代码控制提交。
                real.rollback();
            }
            assertEquals(before, fingerprint(real), "预检后配置数据必须保持原样");
            System.out.println("V080 preflight: " + plannedWrites.get() + " statements; "
                    + (applyRollback ? "actual DML verified and rolled back." : "no database writes executed."));
        }
    }
    private String fingerprint(Connection connection) throws SQLException {
        StringBuilder result = new StringBuilder();
        String[] checks = {
            "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS('|',id,props_document,rules_document,revision))),0) FROM entity_form_node",
            "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS('|',id,draft_hash,revision))),0) FROM entity_form",
            "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS('|',id,draft_document,contract_version,migration_state))),0) FROM entity_version_config",
            "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS('|',id,draft_document,active_release_id,status,migration_state))),0) FROM entity_mutation_policy_config",
            "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS('|',id,config_document))),0) FROM entity_mutation_policy_release"
        };
        for (String sql : checks) {
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
                assertTrue(rs.next()); result.append(rs.getString(1)).append(':').append(rs.getString(2)).append(';');
            }
        }
        return result.toString();
    }

}
