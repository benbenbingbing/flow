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

/** V074 数据库字符集与排序规则统一迁移的轻量契约测试。 */
class DatabaseCollationMigrationTest {

    private static final String MIGRATION_FILE =
            "V074__unify_database_collation.sql";
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void v074FollowsV073AndConvertsCurrentSchemaSafely()
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
        assertTrue(versions.containsAll(List.of(73, 74)));
        assertTrue(versions.indexOf(74) > versions.indexOf(73));

        String sql = Files.readString(migrationDirectory.resolve(
                MIGRATION_FILE));
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("SET v_schema_name = DATABASE()"));
        assertTrue(sql.contains("information_schema.TABLES"));
        assertTrue(sql.contains("information_schema.COLUMNS"));
        assertTrue(sql.contains("TABLE_TYPE = 'BASE TABLE'"));
        assertTrue(sql.contains("CHARACTER SET utf8mb4"));
        assertTrue(sql.contains("COLLATE utf8mb4_unicode_ci"));
        assertTrue(sql.contains("CONVERT TO CHARACTER SET utf8mb4"));
        assertTrue(sql.contains(
                "SET @@SESSION.foreign_key_checks = 0"));
        assertTrue(sql.contains(
                "SET @@SESSION.foreign_key_checks = "
                        + "v_old_foreign_key_checks"));
        assertTrue(sql.contains(
                "TABLE_NAME <> 'flyway_schema_history'"));
        assertTrue(sql.stripTrailing().endsWith(
                "CALL `workflow_v074_unify_database_collation_v2`();"));
        assertFalse(lowerSql.contains("drop procedure"));
        assertFalse(lowerSql.contains("unique_checks = 0"));
        assertFalse(lowerSql.contains("flyway repair"));
    }

    private Path migrationDirectory() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                getClass().getResource("/db/migration"),
                "migration resources are unavailable").toURI());
    }
}
