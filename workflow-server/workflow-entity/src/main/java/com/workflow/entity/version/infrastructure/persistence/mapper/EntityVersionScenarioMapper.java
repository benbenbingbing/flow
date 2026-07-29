package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionScenario;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体版本场景 Mapper。
 */
@Mapper
public interface EntityVersionScenarioMapper
        extends BaseMapper<EntityVersionScenario> {

    @Select("""
            SELECT * FROM entity_version_scenario
            WHERE config_id = #{configId}
            ORDER BY priority DESC, create_time ASC
            """)
    List<EntityVersionScenario> findByConfigId(
            @Param("configId") String configId);

    @Delete("""
            DELETE FROM entity_version_scenario
            WHERE config_id = #{configId}
            """)
    void deleteByConfigId(
            @Param("configId") String configId);
}
