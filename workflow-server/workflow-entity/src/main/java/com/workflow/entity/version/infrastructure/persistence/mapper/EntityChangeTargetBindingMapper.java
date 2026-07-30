package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityChangeTargetBinding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 变更目标绑定 Mapper。
 */
@Mapper
public interface EntityChangeTargetBindingMapper
        extends BaseMapper<EntityChangeTargetBinding> {

    @Select("""
            SELECT * FROM entity_change_target_binding
            WHERE config_id = #{configId}
            ORDER BY create_time ASC
            """)
    List<EntityChangeTargetBinding> findByConfigId(
            @Param("configId") String configId);

    @Delete("""
            DELETE FROM entity_change_target_binding
            WHERE config_id = #{configId}
            """)
    void deleteByConfigId(
            @Param("configId") String configId);
}
