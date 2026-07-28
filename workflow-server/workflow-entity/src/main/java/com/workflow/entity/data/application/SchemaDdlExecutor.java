package com.workflow.entity.data.application;

/**
 * Executes validated dynamic-schema DDL with a dedicated database identity.
 */
@FunctionalInterface
public interface SchemaDdlExecutor {

    void execute(String ddl);
}
