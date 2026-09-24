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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 匹配的实体编码；未找到时为空
     */
    default Optional<EntityCodeRule> findByEntityCode(String entityCode) {
        return Optional.ofNullable(selectOne(Wrappers.<EntityCodeRule>lambdaQuery()
                .eq(EntityCodeRule::getEntityCode, entityCode)));
    }

    /**
     * 原子性更新序列号（使用乐观锁防止并发问题）
     * 返回影响行数，如果为0表示更新失败需要重试
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param oldDate 旧日期，后续用于判断有效期或展示该事件的发生时间
     * @param newDate 新日期，后续用于判断有效期或展示该事件的发生时间
     * @param newSeq 新{@code seq}，供本方法更新{@code seq}日期时使用
     * @return 更新后的{@code seq}日期结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE entity_code_rule SET current_seq = #{newSeq}, seq_date = #{newDate}, update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE entity_code = #{entityCode}
             AND <choose>
                 <when test="oldDate != null">seq_date = #{oldDate}</when>
                 <otherwise>seq_date IS NULL</otherwise>
                 </choose>
            </script>
            """)
    int updateSeqWithDate(@Param("entityCode") String entityCode,
                          @Param("oldDate") String oldDate,
                          @Param("newDate") String newDate,
                          @Param("newSeq") int newSeq);

    /**
     * 更新当前序列号（同一天内递增）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param seqDate {@code seq}日期，后续用于判断有效期或展示该事件的发生时间
     * @param oldSeq 旧{@code seq}，供本方法更新{@code seq}时使用
     * @param newSeq 新{@code seq}，供本方法更新{@code seq}时使用
     * @return 更新后的{@code seq}结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE entity_code_rule SET current_seq = #{newSeq}, update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE entity_code = #{entityCode}
             AND seq_date = #{seqDate}
             AND COALESCE(current_seq, 0) = #{oldSeq}
            </script>
            """)
    int updateSeq(@Param("entityCode") String entityCode,
                  @Param("seqDate") String seqDate,
                  @Param("oldSeq") int oldSeq,
                  @Param("newSeq") int newSeq);

    /** 保存配置时不更新 current_seq/seq_date，避免与独立取号事务竞争而回退流水。 */
    @Update("""
            <script>
            UPDATE entity_code_rule
               SET generation_mode = #{generationMode}, generator_code = #{generatorCode},
                   generator_config = #{generatorConfig,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                   prefix = #{prefix}, date_format = #{dateFormat}, seq_length = #{seqLength},
                   seq_type = #{seqType}, example = #{example},
                   update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE entity_code = #{entityCode}
            </script>
            """)
    int updateConfiguration(EntityCodeRule rule);
}
