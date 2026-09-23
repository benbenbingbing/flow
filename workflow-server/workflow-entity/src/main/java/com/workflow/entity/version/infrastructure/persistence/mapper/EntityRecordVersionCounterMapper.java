package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionCounter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 版本号计数器 Mapper；调用方必须处于事务内。 */
@Mapper
public interface EntityRecordVersionCounterMapper {

    /**
     * 在调用方已持有计数器行锁后追平历史最大值，绝不覆盖更大的版本号。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param initialVersion 初始版本，供本方法处理{@code raise}{@code minimum}时使用
     * @return 处理后的{@code raise}{@code minimum}结果，供调用方继续处理
     */
    @Update("""
            UPDATE entity_record_version_counter
            SET last_version_no = CASE WHEN last_version_no < #{initialVersion}
                                       THEN #{initialVersion} ELSE last_version_no END,
                update_time = CURRENT_TIMESTAMP
            WHERE entity_code = #{entityCode} AND record_id = #{recordId}
            """)
    int raiseMinimum(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("initialVersion") Integer initialVersion);

    /**
     * 锁定实体记录版本计数器；避免后续并发处理覆盖状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 锁定后的实体记录版本计数器结果，供调用方继续处理
     */
    @Select("""
            SELECT entity_code, record_id, last_version_no, update_time
            FROM entity_record_version_counter
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            FOR UPDATE
            """)
    EntityRecordVersionCounter lock(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId);

    /**
     * 更新实体记录版本计数器；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param versionNo 版本号，供本方法更新实体记录版本计数器时使用
     * @return 更新后的实体记录版本计数器结果，供调用方继续处理
     */
    @Update("""
            UPDATE entity_record_version_counter
            SET last_version_no = #{versionNo},
                update_time = CURRENT_TIMESTAMP
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            """)
    int update(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("versionNo") Integer versionNo);
}
