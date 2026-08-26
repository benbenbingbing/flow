package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
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
     * 统计实体发布历史条数。
     *
     * @param entityId 实体定义 ID
     * @return 历史总数
     */
    @Select("SELECT COUNT(*) FROM entity_publish_history WHERE entity_id = #{entityId}")
    long countByEntityId(@Param("entityId") String entityId);

    /**
     * 按版本号倒序分页查询实体发布历史。
     *
     * @param entityId 实体定义 ID
     * @param offset   起始偏移量
     * @param pageSize 本页条数
     * @return 当前页历史记录
     */
    @Select("SELECT * FROM entity_publish_history "
            + "WHERE entity_id = #{entityId} ORDER BY version DESC "
            + "LIMIT #{pageSize} OFFSET #{offset}")
    List<EntityPublishHistory> findPageByEntityId(
            @Param("entityId") String entityId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize);

    /**
     * 利用实体与版本唯一索引精确读取一条发布历史。
     *
     * @param entityId 实体定义 ID
     * @param version  发布版本号
     * @return 指定版本；不存在时返回 null
     */
    @Select("SELECT * FROM entity_publish_history "
            + "WHERE entity_id = #{entityId} AND version = #{version} LIMIT 1")
    EntityPublishHistory findByEntityIdAndVersion(
            @Param("entityId") String entityId,
            @Param("version") Integer version);

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
     * 锁定当前发布历史作为同一实体自关联写入的稳定互斥点。
     * 实体发布先持有 entity_definition 独占锁，因此不会与历史锁形成反序。
     */
    @Select("SELECT * FROM entity_publish_history "
            + "WHERE entity_id = #{entityId} "
            + "ORDER BY version DESC LIMIT 1 FOR UPDATE")
    EntityPublishHistory findLatestByEntityIdForUpdate(
            @Param("entityId") String entityId);

    /**
     * 按实体编码查询最新发布记录
     */
    @Select("SELECT * FROM entity_publish_history WHERE entity_code = #{entityCode} ORDER BY version DESC LIMIT 1")
    EntityPublishHistory findLatestByEntityCode(@Param("entityCode") String entityCode);
}
