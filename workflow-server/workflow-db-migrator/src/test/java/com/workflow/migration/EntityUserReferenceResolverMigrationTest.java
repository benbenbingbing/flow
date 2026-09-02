package com.workflow.migration;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V072 实体用户关系字段人员解析器目录迁移的轻量契约测试。
 */
class EntityUserReferenceResolverMigrationTest {

    private static final String MIGRATION_FILE =
            "V072__entity_user_reference_person_resolver.sql";
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void v072FollowsV071AndSeedsTheEnabledResolverContract()
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
        assertTrue(versions.containsAll(List.of(71, 72)));
        assertTrue(versions.indexOf(72) > versions.indexOf(71));

        String sql = Files.readString(migrationDirectory.resolve(
                MIGRATION_FILE));
        assertTrue(sql.contains("'entityUserReferenceField'"));
        assertTrue(sql.contains(
                "'entityUserReferenceFieldPersonResolver'"));
        assertTrue(sql.contains(
                "'[\"ASSIGNEE\",\"CANDIDATE\",\"MULTI_INSTANCE\"]'"));
        assertTrue(sql.contains(
                "'{\"type\":\"object\",\"additionalProperties\":false,"
                        + "\"required\":[\"schemaVersion\",\"entityCode\","
                        + "\"fieldCode\"],\"properties\":{"
                        + "\"schemaVersion\":{\"const\":1},"
                        + "\"entityCode\":{\"type\":\"string\","
                        + "\"minLength\":1,\"maxLength\":128},"
                        + "\"fieldCode\":{\"type\":\"string\","
                        + "\"minLength\":1,\"maxLength\":100,"
                        + "\"pattern\":\"^[A-Za-z][A-Za-z0-9_]"
                        + "{0,99}$\"}}}'"));
        assertTrue(Pattern.compile(
                        "(?s)\\}\\}\\}'\\s*,\\s*0\\s*,\\s*1\\s*,\\s*1\\s*,")
                .matcher(sql)
                .find(),
                "dynamic_extra_params=0, enabled=1, revision=1 must remain ordered");

    }

    private Path migrationDirectory() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                getClass().getResource("/db/migration"),
                "migration resources are unavailable").toURI());
    }

}
