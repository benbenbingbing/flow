package com.workflow.service;

import com.workflow.common.BusinessConflictException;
import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 实体物理业务表统一解析入口。
 */
@Service
@RequiredArgsConstructor
public class EntityPhysicalTableResolver {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityPhysicalTableNaming naming;
    private final JdbcTemplate jdbcTemplate;

    public String resolve(String entityCode) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException("实体不存在: " + entityCode));
        return resolve(definition);
    }

    public String resolve(EntityDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getEntityCode())) {
            throw new IllegalArgumentException("实体定义或实体编码不能为空");
        }
        if (definition.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_SYSTEM_RUNTIME_NOT_SUPPORTED",
                    "平台系统实体不能通过通用动态实体接口访问: " + definition.getEntityCode());
        }
        assertNotBlocked(definition.getEntityCode());
        if (StringUtils.hasText(definition.getPhysicalTableName())) {
            return naming.validateStoredName(definition.getPhysicalTableName());
        }
        throw new IllegalStateException(
                "实体未登记物理业务表名，请先完成数据库命名迁移: "
                        + definition.getEntityCode());
    }

    public String generate(String entityCode) {
        return naming.generate(entityCode);
    }

    public boolean tableExistsByName(String tableName) {
        return tableExists(naming.validateStoredName(tableName));
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private void assertNotBlocked(String entityCode) {
        try {
            List<String> statuses = jdbcTemplate.query(
                    "SELECT status FROM entity_table_migration_log "
                            + "WHERE entity_code = ? AND status IN ('CONFLICT', 'MISSING') "
                            + "ORDER BY update_time DESC LIMIT 1",
                    (resultSet, rowNum) -> resultSet.getString(1),
                    entityCode);
            if (!statuses.isEmpty()) {
                throw new IllegalStateException(
                        "实体物理表迁移状态异常，已阻止运行: "
                                + entityCode + " / " + statuses.get(0));
            }
        } catch (DataAccessException ignored) {
            // V017 之前或轻量测试环境没有迁移日志表时，保持兼容。
        }
    }
}
