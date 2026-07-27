package com.workflow.entity.data.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 实体物理业务表统一解析入口。
 *
 * <p>根据实体定义解析实际存储数据的物理表名，并阻止系统实体通过
 * 通用动态实体接口访问。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityPhysicalTableResolver {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityPhysicalTableNaming naming;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 根据实体编码解析物理业务表名。
     *
     * @param entityCode 实体编码
     * @return 物理业务表名
     * @throws IllegalArgumentException 实体不存在时抛出
     */
    public String resolve(String entityCode) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException("实体不存在: " + entityCode));
        return resolve(definition);
    }

    /**
     * 根据实体定义解析物理业务表名，并校验系统实体。
     *
     * @param definition 实体定义
     * @return 物理业务表名
     * @throws IllegalArgumentException     实体定义或编码为空时抛出
     * @throws BusinessConflictException    系统实体不允许通过通用接口访问时抛出
     * @throws IllegalStateException         未登记表名时抛出
     */
    public String resolve(EntityDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getEntityCode())) {
            throw new IllegalArgumentException("实体定义或实体编码不能为空");
        }
        if (definition.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_SYSTEM_RUNTIME_NOT_SUPPORTED",
                    "平台系统实体不能通过通用动态实体接口访问: " + definition.getEntityCode());
        }
        if (StringUtils.hasText(definition.getPhysicalTableName())) {
            return naming.validateStoredName(definition.getPhysicalTableName());
        }
        throw new IllegalStateException(
                "实体未登记物理业务表名: "
                        + definition.getEntityCode());
    }

    /**
     * 根据实体编码生成（不落库的）候选物理业务表名。
     *
     * @param entityCode 实体编码
     * @return 候选物理业务表名
     */
    public String generate(String entityCode) {
        return naming.generate(entityCode);
    }

    /**
     * 判断指定表名的物理表是否存在于当前数据库。
     *
     * @param tableName 物理表名，须使用 biz_ 前缀
     * @return 表存在返回 true
     */
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
}
