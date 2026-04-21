package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.ServiceExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 服务执行日志 Mapper
 */
@Mapper
public interface ServiceExecutionLogMapper extends BaseMapper<ServiceExecutionLog> {
    
    /**
     * 分页查询执行日志
     */
    Page<ServiceExecutionLog> selectExecutionList(Page<ServiceExecutionLog> page,
            @Param("serviceId") String serviceId,
            @Param("status") String status);
    
    /**
     * 统计执行次数
     */
    @Select("SELECT COUNT(*) FROM service_execution_log WHERE service_id = #{serviceId}")
    Long countByServiceId(@Param("serviceId") String serviceId);
    
    /**
     * 统计成功次数
     */
    @Select("SELECT COUNT(*) FROM service_execution_log WHERE service_id = #{serviceId} AND status = 'SUCCESS'")
    Long countSuccessByServiceId(@Param("serviceId") String serviceId);
}
