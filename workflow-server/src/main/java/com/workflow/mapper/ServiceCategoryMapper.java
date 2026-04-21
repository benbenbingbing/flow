package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ServiceCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务分类 Mapper
 */
@Mapper
public interface ServiceCategoryMapper extends BaseMapper<ServiceCategory> {
    
    /**
     * 查询所有分类
     */
    @Select("SELECT * FROM service_category ORDER BY sort_order")
    List<ServiceCategory> findAll();
    
    /**
     * 根据父ID查询
     */
    @Select("SELECT * FROM service_category WHERE parent_id = #{parentId} ORDER BY sort_order")
    List<ServiceCategory> findByParentId(@Param("parentId") String parentId);
}
