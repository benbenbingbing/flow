package com.workflow.migration.runner;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import com.workflow.core.database.InitializedDriverDataSource;
import com.workflow.migration.schema.JdbcSchemaChangeWorker;
import com.workflow.integration.database.schema.validation.SchemaStatementScope;
import com.workflow.integration.database.api.DatabaseVendor;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** CLI 启动运行时结构队列；worker 属于本模块，integration 只提供纯方言规则。 */
final class SchemaChangeWorker {
    /**
     * 执行结构变更{@code worker}，并将结果传给后续步骤。
     */
    void run() {
        String url = required("SCHEMA_DATASOURCE_URL");
        var vendor = DatabaseDialects.resolve(environment("WORKFLOW_DATABASE_VENDOR", "auto"), url);
        var source = new InitializedDriverDataSource(url, required("SCHEMA_DB_USERNAME"), required("SCHEMA_DB_PASSWORD"),
                environment("SCHEMA_DB_DRIVER_CLASS_NAME", null), DatabaseJdbcProfiles.connectionInitSql(vendor));
        String owner = environment("SCHEMA_WORKER_ID", "schema-worker-" + UUID.randomUUID());
        var worker = new JdbcSchemaChangeWorker(source, DatabaseDialects.forVendor(vendor), owner);
        var running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false), "schema-worker-shutdown"));
        System.out.println("Schema change worker started: owner=" + owner);
        while (running.get()) {
            try {
                if (worker.processNext()) continue;
            } catch (RuntimeException failure) {
                System.err.println("Schema worker database operation failed: " + failure.getClass().getSimpleName());
            }
            try { Thread.sleep(500); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); running.set(false); }
        }
    }

    /**
     * 保留旧测试入口；生产验证随选择的数据库方言执行。
     *
     * @param ddl DDL，作为 {@code SchemaStatementScope.requireBusinessStatement} 的输入影响后续处理
     */
    static void validate(String ddl) {
        SchemaStatementScope.requireBusinessStatement(ddl, DatabaseDialects.forVendor(DatabaseVendor.MYSQL));
    }

    /**
     * 生成必填文本，供后续匹配或展示。
     *
     * @param name 名称，后续用于处理必填时匹配或展示
     * @return 处理后的必填文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
    /**
     * 生成环境文本，供后续匹配或展示。
     *
     * @param name 名称，后续用于处理环境时匹配或展示
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的环境文本，供调用方比较或展示
     */
    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
