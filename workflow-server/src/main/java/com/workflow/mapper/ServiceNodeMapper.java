package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ServiceNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务节点 Mapper
 */
@Mapper
public interface ServiceNodeMapper extends BaseMapper<ServiceNode> {
    
    /**
     * 根据服务ID查询节点
     */
    @Select("SELECT * FROM service_node WHERE service_id = #{serviceId}")
    List<ServiceNode> findByServiceId(@Param("serviceId") String serviceId);
    
    /**
     * 根据服务ID删除
     */
    @Select("DELETE FROM service_node WHERE service_id = #{serviceId}")
    void deleteByServiceId(@Param("serviceId") String serviceId);
}
