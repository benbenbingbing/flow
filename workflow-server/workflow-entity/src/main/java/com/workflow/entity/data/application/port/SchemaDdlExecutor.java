package com.workflow.entity.data.application.port;

/**
 * Executes validated dynamic-schema DDL with a dedicated database identity.
 */
@FunctionalInterface
public interface SchemaDdlExecutor {

    /**
     * 执行结构DDL执行器，并将结果传给后续步骤。
     *
     * @param ddl DDL，供本方法执行结构DDL执行器时使用
     */
    void execute(String ddl);
}
