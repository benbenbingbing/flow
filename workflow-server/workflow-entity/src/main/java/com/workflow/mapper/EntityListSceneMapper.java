package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScene;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityListSceneMapper extends BaseMapper<EntityListScene> {

    @Select("SELECT * FROM entity_list_scene WHERE list_config_id = #{listConfigId} ORDER BY sort_order")
    List<EntityListScene> findByListConfigId(@Param("listConfigId") String listConfigId);

    @Delete("DELETE FROM entity_list_scene WHERE list_config_id = #{listConfigId}")
    void deleteByListConfigId(@Param("listConfigId") String listConfigId);
}
