package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListAction;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityListActionMapper extends BaseMapper<EntityListAction> {

    @Select("SELECT * FROM entity_list_action "
            + "WHERE list_config_id = #{listConfigId} AND position = #{position} AND deleted = 0 "
            + "ORDER BY order_key, sort_order, create_time")
    List<EntityListAction> findByListAndPosition(
            @Param("listConfigId") String listConfigId,
            @Param("position") String position);

    @Delete("DELETE FROM entity_list_action WHERE list_config_id = #{listConfigId}")
    void deleteByListConfigId(@Param("listConfigId") String listConfigId);
}
