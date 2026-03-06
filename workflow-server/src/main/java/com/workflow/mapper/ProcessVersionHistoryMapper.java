package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessVersionHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 流程版本历史 Mapper
 */
@Mapper
public interface ProcessVersionHistoryMapper extends BaseMapper<ProcessVersionHistory> {

    /**
     * 根据流程定义ID查询版本历史列表（排除已删除）
     */
    @Select("SELECT * FROM process_version_history WHERE process_config_id = #{processConfigId} AND (deleted = 0 OR deleted IS NULL) ORDER BY version DESC")
    List<ProcessVersionHistory> findByProcessConfigId(@Param("processConfigId") String processConfigId);

    /**
     * 查询流程的最大版本号（排除已删除）
     */
    @Select("SELECT MAX(version) FROM process_version_history WHERE process_config_id = #{processConfigId} AND (deleted = 0 OR deleted IS NULL)")
    Integer findMaxVersionByProcessConfigId(@Param("processConfigId") String processConfigId);

    /**
     * 根据流程定义ID和版本号查询（排除已删除）
     */
    @Select("SELECT * FROM process_version_history WHERE process_config_id = #{processConfigId} AND version = #{version} AND (deleted = 0 OR deleted IS NULL)")
    Optional<ProcessVersionHistory> findByProcessConfigIdAndVersion(@Param("processConfigId") String processConfigId, @Param("version") Integer version);

    /**
     * 根据部署ID查询（排除已删除）
     */
    @Select("SELECT * FROM process_version_history WHERE deployment_id = #{deploymentId} AND (deleted = 0 OR deleted IS NULL)")
    Optional<ProcessVersionHistory> findByDeploymentId(@Param("deploymentId") String deploymentId);
}
