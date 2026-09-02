package com.workflow.migration;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V073 仅执行可滚动上线的绑定索引 expand，不提前建立唯一约束。 */
class EntityWorkflowBindingIndexMigrationTest {

    private static final String MIGRATION_FILE =
            "V073__index_entity_workflow_binding.sql";
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void v073IndexesCanonicalAliasesWithoutAddingAUniqueConstraint()
            throws Exception {
        Path migrationDirectory = migrationDirectory();
        List<Integer> versions;
        try (var paths = Files.list(migrationDirectory)) {
            versions = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(VERSIONED_MIGRATION::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted()
                    .toList();
        }

        assertEquals(versions.size(), new HashSet<>(versions).size());
        assertTrue(versions.containsAll(List.of(72, 73)));
        assertTrue(versions.indexOf(73) > versions.indexOf(72));

        String sql = Files.readString(migrationDirectory.resolve(
                MIGRATION_FILE));
        String upperSql = sql.toUpperCase(Locale.ROOT);
        assertTrue(sql.contains(
                "ADD COLUMN `active_process_definition_key` BIGINT"));
        assertTrue(sql.contains("TRIM(LEADING '0' FROM"));
        assertTrue(sql.contains("AS UNSIGNED"));
        assertTrue(sql.contains(") VIRTUAL"));
        assertTrue(sql.contains(
                "ADD KEY `idx_entity_definition_process_binding`"));
        assertTrue(sql.contains("(`active_process_definition_key`)"));
        assertFalse(upperSql.contains("ADD UNIQUE"));
    }

    private Path migrationDirectory() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                getClass().getResource("/db/migration"),
                "migration resources are unavailable").toURI());
    }
}
