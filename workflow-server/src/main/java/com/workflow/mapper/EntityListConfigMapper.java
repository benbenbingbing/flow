package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表配置 Mapper
 */
@Mapper
public interface EntityListConfigMapper extends BaseMapper<EntityListConfig> {

    /**
     * 根据实体ID查询列表配置
     */
    @Select("SELECT * FROM entity_list_config WHERE entity_id = #{entityId} AND deleted = 0 ORDER BY create_time ASC")
    List<EntityListConfig> findByEntityId(@Param("entityId") String entityId);

    /**
     * 根据实体ID和列表标识查询
     */
    @Select("SELECT * FROM entity_list_config WHERE entity_id = #{entityId} AND list_key = #{listKey} AND deleted = 0 LIMIT 1")
    EntityListConfig findByEntityIdAndListKey(@Param("entityId") String entityId, @Param("listKey") String listKey);
}
