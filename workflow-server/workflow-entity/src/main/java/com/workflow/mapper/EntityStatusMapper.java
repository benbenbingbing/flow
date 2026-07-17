package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体状态 Mapper
 */
@Mapper
public interface EntityStatusMapper extends BaseMapper<EntityStatus> {
    
    /**
     * 根据实体编码查询状态列表
     */
    @Select("SELECT * FROM entity_status WHERE entity_code = #{entityCode} AND deleted = 0 ORDER BY sort_order")
    List<EntityStatus> findByEntityCode(@Param("entityCode") String entityCode);
    
    /**
     * 根据实体编码和状态编码查询
     */
    @Select("SELECT * FROM entity_status WHERE entity_code = #{entityCode} AND status_code = #{statusCode} AND deleted = 0 LIMIT 1")
    EntityStatus findByEntityAndCode(@Param("entityCode") String entityCode, @Param("statusCode") String statusCode);
    
    /**
     * 根据分类查询
     */
    @Select("SELECT * FROM entity_status WHERE entity_code = #{entityCode} AND status_category = #{category} AND deleted = 0 ORDER BY sort_order")
    List<EntityStatus> findByCategory(@Param("entityCode") String entityCode, @Param("category") String category);

    /**
     * 物理删除指定实体的所有状态（用于批量保存前清理旧数据）
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM entity_status WHERE entity_code = #{entityCode}")
    void physicalDeleteByEntityCode(@Param("entityCode") String entityCode);
}
