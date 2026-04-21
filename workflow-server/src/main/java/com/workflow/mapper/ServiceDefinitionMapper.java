package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.ServiceDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务定义 Mapper
 */
@Mapper
public interface ServiceDefinitionMapper extends BaseMapper<ServiceDefinition> {
    
    /**
     * 分页查询服务列表
     */
    Page<ServiceDefinition> selectServiceList(Page<ServiceDefinition> page,
            @Param("keyword") String keyword,
            @Param("serviceType") String serviceType,
            @Param("categoryId") String categoryId);
    
    /**
     * 根据编码查询
     */
    @Select("SELECT * FROM service_definition WHERE service_code = #{code} AND status = 'ACTIVE'")
    ServiceDefinition findByCode(@Param("code") String code);
    
    /**
     * 根据分类查询
     */
    @Select("SELECT * FROM service_definition WHERE category_id = #{categoryId} AND status = 'ACTIVE' ORDER BY created_at DESC")
    List<ServiceDefinition> findByCategoryId(@Param("categoryId") String categoryId);
}
