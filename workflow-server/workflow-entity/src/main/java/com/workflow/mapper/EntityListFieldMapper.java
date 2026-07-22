package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListField;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表字段配置 Mapper
 */
@Mapper
public interface EntityListFieldMapper extends BaseMapper<EntityListField> {

    /**
     * 根据列表配置ID查询字段列表
     */
    @Select("SELECT * FROM entity_list_field WHERE list_config_id = #{listConfigId} "
            + "AND deleted = 0 ORDER BY order_key ASC, sort_order ASC")
    List<EntityListField> findByListConfigId(@Param("listConfigId") String listConfigId);

    /**
     * 根据列表配置ID删除字段（物理删除）
     */
    @Delete("DELETE FROM entity_list_field WHERE list_config_id = #{listConfigId}")
    void deleteByListConfigId(@Param("listConfigId") String listConfigId);
}
