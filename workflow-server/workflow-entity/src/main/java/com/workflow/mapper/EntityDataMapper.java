package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 实体数据 Mapper
 */
@Mapper
public interface EntityDataMapper extends BaseMapper<EntityData> {

    /**
     * 根据实体编码查询数据列表
     */
    @Select("SELECT * FROM runtime_entity_record WHERE entity_code = #{entityCode} ORDER BY create_time DESC")
    List<EntityData> findByEntityCode(@Param("entityCode") String entityCode);

    /**
     * 根据ID和实体编码查询
     */
    @Select("SELECT * FROM runtime_entity_record WHERE id = #{id} AND entity_code = #{entityCode}")
    Optional<EntityData> findByIdAndEntityCode(@Param("id") String id, @Param("entityCode") String entityCode);

    /**
     * 根据流程实例ID查询
     */
    @Select("SELECT * FROM runtime_entity_record WHERE process_instance_id = #{processInstanceId}")
    Optional<EntityData> findByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
    
    /**
     * 根据实体编码查询是否有流程数据（process_instance_id不为空）
     */
    @Select("SELECT COUNT(*) FROM runtime_entity_record WHERE entity_code = #{entityCode} AND process_instance_id IS NOT NULL AND deleted = 0")
    int countProcessDataByEntityCode(@Param("entityCode") String entityCode);
}
