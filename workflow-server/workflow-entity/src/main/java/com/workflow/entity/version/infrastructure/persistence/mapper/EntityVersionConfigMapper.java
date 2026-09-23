package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigReadRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体版本配置 Mapper。
 */
@Mapper
public interface EntityVersionConfigMapper
        extends BaseMapper<EntityVersionConfig> {

    /** 同一条 JOIN 读取原始文档，避免分两次查询时读到不同的 active release。 */
    String CURRENT_COLUMNS = """
            SELECT c.id, c.entity_id, c.entity_code, c.enabled, c.config_document,
                   c.revision, c.create_by, c.create_time, c.update_by, c.update_time, c.deleted,
                   r.id AS source_release_id, r.config_document AS source_release_document,
                   r.contract_version AS source_contract_version
            FROM entity_version_config c
            LEFT JOIN entity_version_config_release r
                   ON r.id = c.active_release_id AND r.config_id = c.id
            """;

    /** entity_code/deleted 唯一约束保证至多一行，不需要厂商 LIMIT 语法。 */
    @Select(CURRENT_COLUMNS + " WHERE c.entity_code = #{entityCode} AND c.deleted = 0")
    EntityVersionConfigReadRow selectCurrentRow(@Param("entityCode") String entityCode);

    /**
     * 写入竞争失败后读取当前修订号，避免 MySQL 可重复读快照或 MyBatis 缓存返回旧值。
     * 只读配置基表，不能对带外连接的文档投影加锁；唯一约束保证至多一行。
     * 调用方须处于写事务并随业务冲突结束事务。优先共享读锁，避免重复 INSERT 后锁升级。
     */
    @Select("""
            <script>
            SELECT revision FROM entity_version_config
            WHERE entity_code = #{entityCode,jdbcType=VARCHAR} AND deleted = 0
            ${@com.workflow.integration.database.api.DatabaseQuerySql@readGuard(_databaseId)}
            </script>
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Integer findCurrentRevisionForConflict(@Param("entityCode") String entityCode);

    /** 批量读取包含过渡占位行，运行时和管理列表在投影之后分别保留各自的过滤语义。 */
    @Select(CURRENT_COLUMNS + " WHERE c.deleted = 0 ORDER BY c.entity_code ASC")
    List<EntityVersionConfigReadRow> selectCurrentRows();

    /** 有效 release 优先于当前文档，legacy draft 永远不参与运行时读取。 */
    default EntityVersionConfig findByEntityCode(String entityCode) {
        return CurrentVersionDocumentProjection.resolve(selectCurrentRow(entityCode));
    }

    /** 与旧 SQL 一致：仅保留有效 release 或非 NULL 当前文档对应的行，顺序沿用查询。 */
    default List<EntityVersionConfig> findAllCurrent() {
        return selectCurrentRows().stream().map(CurrentVersionDocumentProjection::resolve)
                .filter(row -> row.getConfigDocument() != null).toList();
    }

    /** 管理列表保留无文档占位行的 id/revision，确保混部时可通过 If-Match 接管。 */
    default List<EntityVersionConfig> findAllForManagementList() {
        return selectCurrentRows().stream().map(CurrentVersionDocumentProjection::resolve).toList();
    }

    /** 按预期修订号原子更新当前文档；冲突返回 0，由调用方读取当前版本并报告。 */
    default int updateCurrentIfRevision(String id, Integer expectedRevision, Boolean enabled,
                                       String configDocument, String updateBy) {
        // 修订号比较和自增留在同一 UPDATE 内，避免先读后写破坏并发控制。
        return update(null, Wrappers.<EntityVersionConfig>lambdaUpdate()
                .eq(EntityVersionConfig::getId, id)
                .eq(EntityVersionConfig::getRevision, expectedRevision)
                .set(EntityVersionConfig::getEnabled, enabled)
                .set(EntityVersionConfig::getConfigDocument, configDocument)
                .set(EntityVersionConfig::getUpdateBy, updateBy)
                .setSql("revision = revision + 1, update_time = CURRENT_TIMESTAMP"));
    }
}
