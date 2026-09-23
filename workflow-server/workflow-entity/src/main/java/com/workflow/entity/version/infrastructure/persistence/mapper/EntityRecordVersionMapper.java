package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;

import java.util.List;

/**
 * 实体数据版本 Mapper。
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityRecordVersionMapper
        extends BaseMapper<EntityRecordVersion> {

    /** 读取业务记录的全部版本，最新版本优先。 */
    default List<EntityRecordVersion> findByRecord(String entityCode, String recordId) {
        return selectList(Wrappers.<EntityRecordVersion>lambdaQuery()
                .eq(EntityRecordVersion::getEntityCode, entityCode)
                .eq(EntityRecordVersion::getRecordId, recordId)
                .orderByDesc(EntityRecordVersion::getVersionNo));
    }

    /** 版本分页只投影摘要列，避免把大快照文档加载到列表中。 */
    default List<EntityRecordVersion> findSummaryPage(String entityCode, String recordId, long offset, long limit) {
        return selectList(new OffsetPage<>(offset, limit), Wrappers.<EntityRecordVersion>lambdaQuery()
                .select(EntityRecordVersion::getId, EntityRecordVersion::getEntityCode,
                        EntityRecordVersion::getRecordId, EntityRecordVersion::getVersionNo,
                        EntityRecordVersion::getVersionTitle, EntityRecordVersion::getScenarioCode,
                        EntityRecordVersion::getScenarioName, EntityRecordVersion::getOperationType,
                        EntityRecordVersion::getSourceType, EntityRecordVersion::getBusinessIntentCode,
                        EntityRecordVersion::getBusinessIntentName, EntityRecordVersion::getOperatorId,
                        EntityRecordVersion::getOperatorName, EntityRecordVersion::getProcessInstanceId,
                        EntityRecordVersion::getSourceEntityCode, EntityRecordVersion::getSourceRecordId,
                        EntityRecordVersion::getSnapshotHash, EntityRecordVersion::getDataHash,
                        EntityRecordVersion::getSchemaVersion, EntityRecordVersion::getScopeHash,
                        EntityRecordVersion::getCreateTime)
                .eq(EntityRecordVersion::getEntityCode, entityCode)
                .eq(EntityRecordVersion::getRecordId, recordId)
                .orderByDesc(EntityRecordVersion::getVersionNo));
    }

    /** 统计指定实体记录的历史版本数，供版本列表分页使用。 */
    default long countByRecord(String entityCode, String recordId) {
        return selectCount(Wrappers.<EntityRecordVersion>lambdaQuery()
                .eq(EntityRecordVersion::getEntityCode, entityCode)
                .eq(EntityRecordVersion::getRecordId, recordId));
    }

    /** 仅投影一行主键判断是否存在历史版本，避免扫描全部版本或加载快照。 */
    default boolean existsByEntityCode(String entityCode) {
        return !selectList(new OffsetPage<>(0, 1), Wrappers.<EntityRecordVersion>lambdaQuery()
                .select(EntityRecordVersion::getId)
                .eq(EntityRecordVersion::getEntityCode, entityCode)).isEmpty();
    }

    /** 仅读取哈希列；兼容旧版本尚未写入 data_hash 时回退 snapshot_hash。 */
    default String findDataHash(String entityCode, String recordId, Integer versionNo) {
        EntityRecordVersion version = selectList(new OffsetPage<>(0, 1), Wrappers.<EntityRecordVersion>lambdaQuery()
                .select(EntityRecordVersion::getDataHash, EntityRecordVersion::getSnapshotHash)
                .eq(EntityRecordVersion::getEntityCode, entityCode)
                .eq(EntityRecordVersion::getRecordId, recordId)
                .eq(EntityRecordVersion::getVersionNo, versionNo))
                .stream().findFirst().orElse(null);
        return version == null ? null : version.getDataHash() == null ? version.getSnapshotHash() : version.getDataHash();
    }

    default EntityRecordVersion findVersion(String entityCode, String recordId, Integer versionNo) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityRecordVersion>lambdaQuery()
                .eq(EntityRecordVersion::getEntityCode, entityCode)
                .eq(EntityRecordVersion::getRecordId, recordId)
                .eq(EntityRecordVersion::getVersionNo, versionNo))
                .stream().findFirst().orElse(null);
    }

    default Integer findMaxVersionNo(String entityCode, String recordId) {
        List<Object> values = selectObjs(Wrappers.<EntityRecordVersion>query()
                .select("MAX(version_no)")
                .eq("entity_code", entityCode)
                .eq("record_id", recordId));
        return values.isEmpty() || values.get(0) == null ? 0 : ((Number) values.get(0)).intValue();
    }

    // V046 已将幂等键收紧为 entity_code/record_id/idempotency_key，至多返回一行。
    /** 按实体、记录及幂等键读取已创建版本；未命中返回 null，避免重试重复创建。 */
    default EntityRecordVersion findIdempotent(String entityCode, String recordId, String idempotencyKey) {
        return selectOne(Wrappers.<EntityRecordVersion>lambdaQuery()
                .eq(EntityRecordVersion::getEntityCode, entityCode)
                .eq(EntityRecordVersion::getRecordId, recordId)
                .eq(EntityRecordVersion::getIdempotencyKey, idempotencyKey));
    }

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
            SELECT * FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
              AND idempotency_key = #{idempotencyKey}
            FOR UPDATE
            """)
    EntityRecordVersion findIdempotentForUpdate(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("idempotencyKey") String idempotencyKey);

    /** 按主键读取版本快照，沿用实体字段映射。 */
    default EntityRecordVersion findById(String id) {
        return selectById(id);
    }
}
