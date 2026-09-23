package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityMutationReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 实体变更幂等回执 Mapper。
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityMutationReceiptMapper
        extends BaseMapper<EntityMutationReceipt> {

    default EntityMutationReceipt findByIdempotencyKey(String idempotencyKey) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityMutationReceipt>lambdaQuery()
                .eq(EntityMutationReceipt::getIdempotencyKey, idempotencyKey))
                .stream().findFirst().orElse(null);
    }

    /**
     * 唯一冲突后当前读取已提交回执；只重放、不再修改，不需要排他锁。
     * MySQL 的重复 INSERT 已持有共享锁，多个竞争者升级 FOR UPDATE 会相互死锁。
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
            <script>
            SELECT * FROM entity_mutation_receipt
            WHERE idempotency_key = #{idempotencyKey}
            ${@com.workflow.integration.database.api.DatabaseQuerySql@readGuard(_databaseId)}
            </script>
            """)
    EntityMutationReceipt findByIdempotencyKeyForReplay(
            @Param("idempotencyKey") String idempotencyKey);

    /** 仅将尚在处理的幂等回执原子置为成功；重复完成返回 0，不覆盖已存结果。 */
    default int complete(String idempotencyKey, String recordId, String resultDocument,
                         Integer versionNo, String versionScenarioCode, Boolean changed) {
        return update(null, Wrappers.<EntityMutationReceipt>lambdaUpdate()
                .eq(EntityMutationReceipt::getIdempotencyKey, idempotencyKey)
                .eq(EntityMutationReceipt::getStatus, "PENDING")
                .set(EntityMutationReceipt::getRecordId, recordId)
                .set(EntityMutationReceipt::getStatus, "SUCCESS")
                .set(EntityMutationReceipt::getResultDocument, resultDocument)
                .set(EntityMutationReceipt::getVersionNo, versionNo)
                .set(EntityMutationReceipt::getVersionScenarioCode, versionScenarioCode)
                .set(EntityMutationReceipt::getChanged, changed)
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }
}
