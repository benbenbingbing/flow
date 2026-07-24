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

    /**
     * 根据实体编码和列表标识查询列表配置
     *
     * @param entityCode 实体编码
     * @param listKey    列表标识
     * @return 列表配置，无则返回 null
     */
    @Select("SELECT * FROM entity_list_config "
            + "WHERE entity_code = #{entityCode} AND list_key = #{listKey} "
            + "AND deleted = 0 LIMIT 1")
    EntityListConfig findByEntityCodeAndListKey(
            @Param("entityCode") String entityCode,
            @Param("listKey") String listKey);

    /**
     * 根据实体编码查询全部列表配置，默认列表优先，按创建时间升序排列。
     *
     * @param entityCode 实体编码
     * @return 列表配置列表
     */
    @Select("SELECT * FROM entity_list_config "
            + "WHERE entity_code = #{entityCode} AND deleted = 0 "
            + "ORDER BY is_default DESC, create_time ASC")
    List<EntityListConfig> findByEntityCode(@Param("entityCode") String entityCode);

    /**
     * 根据主键 ID 加锁查询列表配置（FOR UPDATE），用于并发更新场景。
     *
     * @param id 主键 ID
     * @return 列表配置，无则返回 null
     */
    @Select("SELECT * FROM entity_list_config WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EntityListConfig selectByIdForUpdate(@Param("id") String id);
}
