package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityPublishHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper

/**
 * 实体发布版本历史Mapper
 */
public interface EntityPublishHistoryMapper extends BaseMapper<EntityPublishHistory> {

    /**
     * 根据实体ID查询版本历史列表（按版本号降序）
     */
    @Select("SELECT * FROM entity_publish_history WHERE entity_id = #{entityId} ORDER BY version DESC")
    List<EntityPublishHistory> findByEntityId(@Param("entityId") String entityId);

    /**
     * 获取实体的最新版本号
     */
    @Select("SELECT MAX(version) FROM entity_publish_history WHERE entity_id = #{entityId}")
    Integer getLatestVersion(@Param("entityId") String entityId);

    /**
     * 查询实体的最新发布记录
     */
    @Select("SELECT * FROM entity_publish_history WHERE entity_id = #{entityId} ORDER BY version DESC LIMIT 1")
    EntityPublishHistory findLatestByEntityId(@Param("entityId") String entityId);

    /**
     * 按实体编码查询最新发布记录
     */
    @Select("SELECT * FROM entity_publish_history WHERE entity_code = #{entityCode} ORDER BY version DESC LIMIT 1")
    EntityPublishHistory findLatestByEntityCode(@Param("entityCode") String entityCode);
}
