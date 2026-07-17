package com.workflow.runner;

import com.workflow.service.EntityPhysicalTableNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 历史动态实体表命名迁移：entity_data_{code} -> biz_{code}。
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class EntityTableNamingMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final EntityPhysicalTableNaming naming;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("entity_definition")
                || !tableExists("entity_table_migration_log")) {
            log.debug("实体物理表命名迁移所需表不存在，跳过");
            return;
        }
        List<Map<String, Object>> entities = jdbcTemplate.queryForList(
                "SELECT id, entity_code, table_name, status "
                        + "FROM entity_definition WHERE deleted = 0");
        Set<String> activeLegacyTables = new HashSet<>();
        for (Map<String, Object> entity : entities) {
            activeLegacyTables.add(naming.legacyName(text(entity.get("entity_code"))));
            migrate(entity);
        }
        migrateOrphanLegacyTables(activeLegacyTables);
    }

    private void migrate(Map<String, Object> entity) {
        String entityId = text(entity.get("id"));
        String entityCode = text(entity.get("entity_code"));
        String configuredName = text(entity.get("table_name"));
        String entityStatus = text(entity.get("status"));
        String legacyName;
        String targetName;
        try {
            legacyName = naming.legacyName(entityCode);
            targetName = naming.generate(entityCode);
        } catch (RuntimeException exception) {
            record(entityCode, configuredName, null, "FAILED", null, null, exception.getMessage());
            return;
        }

        try {
            boolean legacyExists = tableExists(legacyName);
            boolean targetExists = tableExists(targetName);
            String configured = StringUtils.hasText(configuredName)
                    ? naming.validateStoredName(configuredName)
                    : null;
            boolean configuredExists = StringUtils.hasText(configured) && tableExists(configured);

            String sourceName = configuredExists && !targetName.equals(configured)
                    ? configured
                    : legacyExists ? legacyName : null;

            if (targetExists && StringUtils.hasText(sourceName) && !targetName.equals(sourceName)) {
                record(entityCode, sourceName, targetName, "CONFLICT",
                        countRows(sourceName), countRows(targetName),
                        "源表和目标表同时存在，禁止自动覆盖");
                return;
            }

            if (targetExists) {
                updatePhysicalName(entityId, targetName);
                record(entityCode, configuredName, targetName, "SUCCESS",
                        countRows(targetName), countRows(targetName), null);
                return;
            }

            if (StringUtils.hasText(sourceName)) {
                long sourceCount = countRows(sourceName);
                renameTable(sourceName, targetName);
                renameIndexes(sourceName, targetName);
                long targetCount = countRows(targetName);
                if (sourceCount != targetCount) {
                    record(entityCode, sourceName, targetName, "FAILED",
                            sourceCount, targetCount, "迁移前后行数不一致");
                    return;
                }
                updatePhysicalName(entityId, targetName);
                record(entityCode, sourceName, targetName, "SUCCESS",
                        sourceCount, targetCount, null);
                log.info("实体物理表命名迁移完成: entityCode={}, {} -> {}",
                        entityCode, sourceName, targetName);
                return;
            }

            if ("DRAFT".equalsIgnoreCase(entityStatus)) {
                updatePhysicalName(entityId, targetName);
                record(entityCode, configuredName, targetName, "PENDING",
                        0L, 0L, "草稿实体尚未创建物理表");
                return;
            }

            record(entityCode, configuredName, targetName, "MISSING",
                    null, null, "已发布实体的物理表不存在");
        } catch (RuntimeException exception) {
            record(entityCode, configuredName, targetName, "FAILED",
                    null, null, exception.getMessage());
            log.error("实体物理表命名迁移失败: entityCode={}", entityCode, exception);
        }
    }

    private void renameTable(String sourceName, String targetName) {
        jdbcTemplate.execute("RENAME TABLE `" + sourceName + "` TO `" + targetName + "`");
    }

    private void migrateOrphanLegacyTables(Set<String> activeLegacyTables) {
        List<String> legacyTables = jdbcTemplate.query(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name LIKE 'entity_data\\_%'",
                (resultSet, rowNum) -> resultSet.getString(1));
        for (String tableName : legacyTables) {
            String sourceName = naming.validateMigrationName(tableName);
            if (activeLegacyTables.contains(sourceName)) {
                continue;
            }
            String suffix = sourceName.substring(EntityPhysicalTableNaming.LEGACY_PREFIX.length());
            String targetName = naming.generate(suffix);
            String migrationKey = "orphan_" + suffix;
            try {
                if (tableExists(targetName)) {
                    record(migrationKey, sourceName, targetName, "CONFLICT",
                            countRows(sourceName), countRows(targetName),
                            "孤立旧表的目标表已存在，禁止自动覆盖");
                    continue;
                }
                long sourceCount = countRows(sourceName);
                renameTable(sourceName, targetName);
                renameIndexes(sourceName, targetName);
                long targetCount = countRows(targetName);
                if (sourceCount != targetCount) {
                    record(migrationKey, sourceName, targetName, "FAILED",
                            sourceCount, targetCount, "孤立旧表迁移前后行数不一致");
                    continue;
                }
                record(migrationKey, sourceName, targetName, "SUCCESS",
                        sourceCount, targetCount, "孤立旧表已保留数据并完成命名隔离");
                log.warn("孤立实体业务表命名迁移完成: {} -> {}, rows={}",
                        sourceName, targetName, targetCount);
            } catch (RuntimeException exception) {
                record(migrationKey, sourceName, targetName, "FAILED",
                        null, null, exception.getMessage());
                log.error("孤立实体业务表命名迁移失败: {}", sourceName, exception);
            }
        }
    }

    private void renameIndexes(String sourceName, String targetName) {
        List<String> indexNames = jdbcTemplate.query(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND index_name <> 'PRIMARY' AND index_name LIKE ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                targetName,
                "%" + sourceName + "%");
        for (String oldIndexName : indexNames) {
            String newIndexName = oldIndexName.replace(sourceName, targetName);
            if (newIndexName.length() > 64 || oldIndexName.equals(newIndexName)) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE `" + targetName + "` RENAME INDEX `"
                    + oldIndexName + "` TO `" + newIndexName + "`");
        }
    }

    private void updatePhysicalName(String entityId, String tableName) {
        jdbcTemplate.update(
                "UPDATE entity_definition SET table_name = ? WHERE id = ?",
                tableName,
                entityId);
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + naming.validateMigrationName(tableName) + "`",
                Long.class);
        return count == null ? 0L : count;
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class,
                    tableName);
            return count != null && count > 0;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private void record(
            String entityCode,
            String sourceTable,
            String targetTable,
            String status,
            Long sourceCount,
            Long targetCount,
            String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO entity_table_migration_log ("
                        + "id, entity_code, source_table, target_table, status, "
                        + "source_row_count, target_row_count, error_message, retry_count, "
                        + "started_at, finished_at, create_time, update_time"
                        + ") VALUES (UUID(), ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, NOW(), NOW()) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "source_table = VALUES(source_table), "
                        + "target_table = VALUES(target_table), "
                        + "status = VALUES(status), "
                        + "source_row_count = VALUES(source_row_count), "
                        + "target_row_count = VALUES(target_row_count), "
                        + "error_message = VALUES(error_message), "
                        + "retry_count = retry_count + 1, "
                        + "started_at = VALUES(started_at), "
                        + "finished_at = VALUES(finished_at), "
                        + "update_time = NOW()",
                entityCode,
                sourceTable,
                targetTable,
                status,
                sourceCount,
                targetCount,
                errorMessage,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
