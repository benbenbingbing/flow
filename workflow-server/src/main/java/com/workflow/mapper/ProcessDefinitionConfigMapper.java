package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessDefinitionConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 流程定义配置 Mapper
 */
@Mapper
public interface ProcessDefinitionConfigMapper extends BaseMapper<ProcessDefinitionConfig> {

    /**
     * 根据流程标识查询（排除已删除）
     */
    @Select("SELECT * FROM process_definition_config WHERE process_key = #{processKey} AND (deleted = 0 OR deleted IS NULL)")
    Optional<ProcessDefinitionConfig> findByProcessKey(@Param("processKey") String processKey);

    /**
     * 根据状态查询（排除已删除）
     */
    @Select("SELECT * FROM process_definition_config WHERE status = #{status} AND (deleted = 0 OR deleted IS NULL)")
    List<ProcessDefinitionConfig> findByStatus(@Param("status") String status);

    /**
     * 检查流程标识是否存在（排除已删除）
     */
    @Select("SELECT COUNT(*) > 0 FROM process_definition_config WHERE process_key = #{processKey} AND (deleted = 0 OR deleted IS NULL)")
    boolean existsByProcessKey(@Param("processKey") String processKey);

    /**
     * 查询所有流程（排除已删除）
     */
    @Select("SELECT * FROM process_definition_config WHERE deleted = 0 OR deleted IS NULL ORDER BY update_time DESC")
    List<ProcessDefinitionConfig> findAllActive();

    /**
     * 查询所有未被实体绑定的流程（排除已删除）
     * 用于实体绑定流程时选择
     */
    @Select("SELECT p.* FROM process_definition_config p " +
            "WHERE (p.deleted = 0 OR p.deleted IS NULL) " +
            "AND p.id NOT IN (SELECT e.process_definition_id FROM entity_definition e WHERE e.process_definition_id IS NOT NULL) " +
            "ORDER BY p.update_time DESC")
    List<ProcessDefinitionConfig> findAllUnbound();
}
