package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionStep;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体版本前置操作 Mapper。
 */
@Mapper
public interface EntityVersionStepMapper
        extends BaseMapper<EntityVersionStep> {

    @Select("""
            SELECT * FROM entity_version_step
            WHERE config_id = #{configId}
            ORDER BY sort_order ASC, create_time ASC
            """)
    List<EntityVersionStep> findByConfigId(
            @Param("configId") String configId);

    @Delete("""
            DELETE FROM entity_version_step
            WHERE config_id = #{configId}
            """)
    void deleteByConfigId(
            @Param("configId") String configId);
}
