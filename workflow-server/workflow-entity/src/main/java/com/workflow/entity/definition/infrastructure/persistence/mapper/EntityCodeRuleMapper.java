package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 实体编码规则Mapper
 */
@Mapper
public interface EntityCodeRuleMapper extends BaseMapper<EntityCodeRule> {

    /**
     * 根据实体编码查询编码规则
     */
    default Optional<EntityCodeRule> findByEntityCode(String entityCode) {
        return Optional.ofNullable(selectOne(Wrappers.<EntityCodeRule>lambdaQuery()
                .eq(EntityCodeRule::getEntityCode, entityCode)));
    }

    /**
     * 原子性更新序列号（使用乐观锁防止并发问题）
     * 返回影响行数，如果为0表示更新失败需要重试
     */
    @Update("""
            <script>
            UPDATE entity_code_rule SET current_seq = #{newSeq}, seq_date = #{newDate}, update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE entity_code = #{entityCode}
             AND seq_date = #{oldDate}
            </script>
            """)
    int updateSeqWithDate(@Param("entityCode") String entityCode,
                          @Param("oldDate") String oldDate,
                          @Param("newDate") String newDate,
                          @Param("newSeq") int newSeq);

    /**
     * 更新当前序列号（同一天内递增）
     */
    @Update("""
            <script>
            UPDATE entity_code_rule SET current_seq = #{newSeq}, update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE entity_code = #{entityCode}
             AND seq_date = #{seqDate}
             AND current_seq = #{oldSeq}
            </script>
            """)
    int updateSeq(@Param("entityCode") String entityCode,
                  @Param("seqDate") String seqDate,
                  @Param("oldSeq") int oldSeq,
                  @Param("newSeq") int newSeq);
}
