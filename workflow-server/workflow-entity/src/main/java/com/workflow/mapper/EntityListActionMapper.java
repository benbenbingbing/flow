package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListAction;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表操作项 Mapper
 * 
 * 提供按列表配置 ID 和位置（工具栏/行操作）查询操作项，以及按列表配置 ID 清空操作项的能力。
 */
@Mapper
public interface EntityListActionMapper extends BaseMapper<EntityListAction> {

    /**
     * 根据列表配置 ID 和位置查询未删除的操作项列表，按 order_key、sort_order、create_time 排序。
     *
     * @param listConfigId 列表配置 ID
     * @param position     位置标识（如 toolbar/row）
     * @return 操作项列表
     */
    @Select("SELECT * FROM entity_list_action "
            + "WHERE list_config_id = #{listConfigId} AND position = #{position} AND deleted = 0 "
            + "ORDER BY order_key, sort_order, create_time")
    List<EntityListAction> findByListAndPosition(
            @Param("listConfigId") String listConfigId,
            @Param("position") String position);

    /**
     * 根据列表配置 ID 物理删除所有操作项（用于批量保存前清理旧数据）。
     *
     * @param listConfigId 列表配置 ID
     */
    @Delete("DELETE FROM entity_list_action WHERE list_config_id = #{listConfigId}")
    void deleteByListConfigId(@Param("listConfigId") String listConfigId);
}
