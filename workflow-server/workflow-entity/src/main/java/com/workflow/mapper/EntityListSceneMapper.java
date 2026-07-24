package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScene;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表场景 Mapper
 * 
 * 提供按列表配置 ID 查询场景列表及按列表配置 ID 删除场景的能力。
 */
@Mapper
public interface EntityListSceneMapper extends BaseMapper<EntityListScene> {

    /**
     * 根据列表配置 ID 查询场景列表，按 sort_order 排序。
     *
     * @param listConfigId 列表配置 ID
     * @return 场景列表
     */
    @Select("SELECT * FROM entity_list_scene WHERE list_config_id = #{listConfigId} ORDER BY sort_order")
    List<EntityListScene> findByListConfigId(@Param("listConfigId") String listConfigId);

    /**
     * 根据列表配置 ID 物理删除所有场景（用于批量保存前清理旧数据）。
     *
     * @param listConfigId 列表配置 ID
     */
    @Delete("DELETE FROM entity_list_scene WHERE list_config_id = #{listConfigId}")
    void deleteByListConfigId(@Param("listConfigId") String listConfigId);
}
