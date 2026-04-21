package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体字段 Mapper
 */
@Mapper
public interface EntityFieldMapper extends BaseMapper<EntityField> {

    /**
     * 根据实体ID查询字段列表
     */
    @Select("SELECT * FROM entity_field WHERE entity_id = #{entityId} ORDER BY sort_order ASC")
    List<EntityField> findByEntityId(@Param("entityId") String entityId);

    /**
     * 根据实体ID删除字段
     */
    @Select("DELETE FROM entity_field WHERE entity_id = #{entityId}")
    void deleteByEntityId(@Param("entityId") String entityId);

    /**
     * 根据实体ID和字段编码查询字段
     */
    @Select("SELECT * FROM entity_field WHERE entity_id = #{entityId} AND field_code = #{fieldCode} LIMIT 1")
    EntityField findByEntityIdAndFieldCode(@Param("entityId") String entityId, @Param("fieldCode") String fieldCode);

    /**
     * 根据ID查询字段（ID是String类型，数据库是bigint，会自动转换）
     */
    @Select("SELECT * FROM entity_field WHERE id = #{id} LIMIT 1")
    EntityField findByIdString(@Param("id") String id);
}
