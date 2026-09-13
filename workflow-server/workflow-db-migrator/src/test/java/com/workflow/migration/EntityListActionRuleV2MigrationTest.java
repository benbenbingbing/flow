package com.workflow.migration;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V085 列表按钮条件 v1 到 v2 数据迁移的轻量契约测试。 */
class EntityListActionRuleV2MigrationTest {

    private static final String MIGRATION_FILE =
            "V085__upgrade_entity_list_action_rules_to_v2.sql";

    @Test
    void upgradesDraftRuleSourcesWithoutMutatingImmutableReleases()
            throws Exception {
        String sql = Files.readString(migrationPath());
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("'visibleWhen', JSON_EXTRACT("));
        assertTrue(sql.contains("'enabledWhen', JSON_EXTRACT("));
        assertTrue(sql.contains("'disabledMessage', LEFT(COALESCE("));
        assertTrue(sql.contains("JOIN JSON_TABLE("));
        assertTrue(sql.contains("JSON_ARRAYAGG(`button`) OVER ("));
        assertTrue(sql.contains(
                "ROWS BETWEEN UNBOUNDED PRECEDING "
                        + "AND UNBOUNDED FOLLOWING"));
        assertTrue(sql.contains("UPDATE `entity_list_action`"));
        assertTrue(sql.contains("UPDATE `entity_list_config`"));
        assertTrue(sql.contains("'$.availabilityRule'"));
        assertTrue(sql.contains("a.`revision` = a.`revision` + 1"));
        assertTrue(sql.contains("c.`revision` = c.`revision` + 1"));
        assertTrue(sql.contains("c.`draft_hash` = NULL"));
        assertFalse(lowerSql.contains("update `ui_config_release`"));
        assertFalse(lowerSql.contains("create function"));
        assertFalse(lowerSql.contains("create procedure"));
        assertFalse(lowerSql.contains("drop function"));
        assertFalse(lowerSql.contains("delimiter"));
        assertFalse(lowerSql.contains("flyway repair"));
    }

    private Path migrationPath() throws URISyntaxException {
        Path directory = Path.of(Objects.requireNonNull(
                getClass().getResource("/db/migration"),
                "migration resources are unavailable").toURI());
        return directory.resolve(MIGRATION_FILE);
    }
}
