package com.workflow.db;

import com.workflow.admin.extension.action.api.web.FlowActionCatalogController;
import com.workflow.entity.data.api.web.EntityFlowStatusController;
import com.workflow.process.action.api.web.FlowActionController;
import com.workflow.process.action.api.web.FlowActionExecutionController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards canonical database, Java persistence, and API naming.
 */
class DatabaseNamingIsolationTest {

    private static final String LEGACY_CREATE_TIME =
            "created" + "_at";
    private static final String LEGACY_UPDATE_TIME =
            "updated" + "_at";
    private static final Pattern LEGACY_RUNTIME_REFERENCE = Pattern.compile(
            "@TableName\\(\\\"(entity_data|node_config|assignee_config|form_config|"
                    + "form_field_config|flow_action|flow_action_definition|"
                    + "flow_action_execution|entity_flow_status_mapping)\\\"\\)"
                    + "|(?:FROM|UPDATE|DELETE FROM)\\s+"
                    + "(entity_data|node_config|assignee_config|form_config|"
                    + "form_field_config|flow_action|flow_action_definition|"
                    + "flow_action_execution|entity_flow_status_mapping)\\b");

    @Test
    void baselineContainsOnlyCanonicalStaticTableNames() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V001__business_schema.sql"));
        for (String table : List.of(
                "process_node_config",
                "process_node_assignee",
                "process_form_config",
                "process_form_field_config",
                "process_action",
                "process_action_definition",
                "process_action_execution",
                "process_entity_status_mapping")) {
            assertTrue(sql.contains("CREATE TABLE `" + table + "`"));
        }
        for (String table : List.of(
                "entity_data",
                "node_config",
                "assignee_config",
                "form_config",
                "form_field_config",
                "flow_action",
                "flow_action_definition",
                "flow_action_execution",
                "entity_flow_status_mapping")) {
            assertFalse(sql.contains("CREATE TABLE `" + table + "`"));
        }
    }

    @Test
    void runtimeJavaSourcesUseCanonicalTablesAndTimestampColumns()
            throws Exception {
        for (Path module : List.of(
                Path.of("../workflow-entity/src/main/java"),
                Path.of("../workflow-process/src/main/java"),
                Path.of("../workflow-migration/src/main/java"),
                Path.of("../workflow-admin/src/main/java"),
                Path.of("../workflow-integration/src/main/java"))) {
            if (!Files.exists(module)) {
                continue;
            }
            try (var files = Files.walk(module)) {
                List<Path> violations = files
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> violatesRuntimeNaming(path))
                        .toList();
                assertTrue(violations.isEmpty(),
                        "legacy persistence naming remains: " + violations);
            }
        }
    }

    @Test
    void persistenceRecordsMapApiTimestampsExplicitly() throws Exception {
        for (Path module : List.of(
                Path.of("../workflow-entity/src/main/java"),
                Path.of("../workflow-process/src/main/java"),
                Path.of("../workflow-migration/src/main/java"),
                Path.of("../workflow-admin/src/main/java"),
                Path.of("../workflow-integration"))) {
            if (!Files.exists(module)) {
                continue;
            }
            try (var files = Files.walk(module)) {
                List<Path> violations = files
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> hasImplicitTimestampMapping(path))
                        .toList();
                assertTrue(violations.isEmpty(),
                        "implicit timestamp mappings remain: " + violations);
            }
        }
    }

    @Test
    void controllersExposeCanonicalProcessApis() {
        assertRequestMapping(FlowActionController.class, "/api/process-actions");
        assertRequestMapping(
                FlowActionCatalogController.class,
                "/api/process-action-handlers");
        assertRequestMapping(
                FlowActionExecutionController.class,
                "/api/process-action-executions");
        assertRequestMapping(
                EntityFlowStatusController.class,
                "/api/process-entity-status-mappings");
    }

    @Test
    void frontendUsesCanonicalProcessAndEntityApis() throws Exception {
        Path webRoot = Path.of("../../workflow-web/src");
        String actionApi = Files.readString(
                webRoot.resolve("api/processAction.js"));
        assertFalse(Files.exists(webRoot.resolve("api/flowAction.js")));
        for (String endpoint : List.of(
                "/process-actions",
                "/process-action-handlers",
                "/process-action-executions")) {
            assertTrue(actionApi.contains(endpoint));
        }

        String scopeApi = Files.readString(
                webRoot.resolve("api/entityListScope.js"));
        assertTrue(scopeApi.contains("/entity-list-scopes"));
        assertFalse(scopeApi.contains("/entity-list-permission"));
    }

    @Test
    void dynamicTablesUseCanonicalPrefixAndUnicodeCollation()
            throws Exception {
        String naming = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/entity/data/"
                        + "application/EntityPhysicalTableNaming.java"));
        String dynamicTable = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/entity/data/"
                        + "application/DynamicTableService.java"));
        String teamTable = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/entity/data/"
                        + "application/EntityRecordTeamService.java"));

        assertTrue(naming.contains("BUSINESS_PREFIX = \"biz_\""));
        assertFalse(naming.contains("LEGACY_PREFIX"));
        assertTrue(dynamicTable.contains("COLLATE=utf8mb4_unicode_ci"));
        assertTrue(teamTable.contains("COLLATE=utf8mb4_unicode_ci"));
    }

    private boolean violatesRuntimeNaming(Path path) {
        try {
            String source = Files.readString(path);
            return LEGACY_RUNTIME_REFERENCE.matcher(source).find()
                    || Pattern.compile(
                            "\\b(" + LEGACY_CREATE_TIME + "|"
                                    + LEGACY_UPDATE_TIME + ")\\b")
                    .matcher(source)
                    .find()
                    || source.contains("entity_table_migration_log");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean hasImplicitTimestampMapping(Path path) {
        try {
            String source = Files.readString(path);
            if (!source.contains("@TableName")) {
                return false;
            }
            return missingMapping(source, "createdAt", "create_time")
                    || missingMapping(source, "updatedAt", "update_time");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean missingMapping(
            String source,
            String field,
            String column) {
        int index = source.indexOf(" " + field + ";");
        if (index < 0) {
            return false;
        }
        String prefix = source.substring(Math.max(0, index - 300), index);
        return !Pattern.compile(
                        "@TableField\\s*\\([^)]*" + column + "[^)]*\\)",
                        Pattern.DOTALL)
                .matcher(prefix)
                .find();
    }

    private void assertRequestMapping(
            Class<?> controllerType,
            String expected) {
        RequestMapping mapping =
                controllerType.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{expected}, mapping.value());
    }
}
