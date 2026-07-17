package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FlowAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FlowActionMapper extends BaseMapper<FlowAction> {
    
    /**
     * 查询流程配置下所有草稿状态的动作（排除已删除）
     */
    @Select("SELECT * FROM process_action WHERE process_config_id = #{processConfigId} AND status = 'DRAFT' AND (deleted = 0 OR deleted IS NULL) ORDER BY sequence_flow_id, sort_order")
    List<FlowAction> findDraftActionsByProcessConfigId(@Param("processConfigId") String processConfigId);
    
    /**
     * 查询顺序流下所有草稿状态的动作（排除已删除）
     */
    @Select("SELECT * FROM process_action WHERE process_config_id = #{processConfigId} AND sequence_flow_id = #{sequenceFlowId} AND status = 'DRAFT' AND (deleted = 0 OR deleted IS NULL) ORDER BY sort_order")
    List<FlowAction> findDraftActionsBySequenceFlowId(@Param("processConfigId") String processConfigId, @Param("sequenceFlowId") String sequenceFlowId);

    @Select("SELECT * FROM process_action " +
            "WHERE process_config_id = #{processConfigId} " +
            "  AND scope_type = #{scopeType} " +
            "  AND ((#{elementId} IS NULL AND element_id IS NULL) OR element_id = #{elementId}) " +
            "  AND status = 'DRAFT' " +
            "  AND (deleted = 0 OR deleted IS NULL) " +
            "ORDER BY trigger_timing, sort_order")
    List<FlowAction> findDraftActionsByBinding(
            @Param("processConfigId") String processConfigId,
            @Param("scopeType") String scopeType,
            @Param("elementId") String elementId);
    
    /**
     * 查询版本下所有已发布的动作（排除已删除）
     */
    @Select("SELECT * FROM process_action WHERE version_id = #{versionId} AND status = 'PUBLISHED' AND (deleted = 0 OR deleted IS NULL) ORDER BY sequence_flow_id, sort_order")
    List<FlowAction> findPublishedActionsByVersionId(@Param("versionId") String versionId);
    
    /**
     * 查询版本下特定顺序流的动作（排除已删除）
     */
    @Select("SELECT * FROM process_action WHERE version_id = #{versionId} AND sequence_flow_id = #{sequenceFlowId} AND status = 'PUBLISHED' AND (deleted = 0 OR deleted IS NULL) ORDER BY sort_order")
    List<FlowAction> findPublishedActionsBySequenceFlowId(@Param("versionId") String versionId, @Param("sequenceFlowId") String sequenceFlowId);

    @Select("SELECT * FROM process_action " +
            "WHERE version_id = #{versionId} " +
            "  AND scope_type = #{scopeType} " +
            "  AND ((#{elementId} IS NULL AND element_id IS NULL) OR element_id = #{elementId}) " +
            "  AND trigger_timing = #{triggerTiming} " +
            "  AND status = 'PUBLISHED' " +
            "  AND (deleted = 0 OR deleted IS NULL) " +
            "ORDER BY sort_order")
    List<FlowAction> findPublishedActionsByBinding(
            @Param("versionId") String versionId,
            @Param("scopeType") String scopeType,
            @Param("elementId") String elementId,
            @Param("triggerTiming") String triggerTiming);
    
    /**
     * 逻辑删除动作
     */
    @Update("UPDATE process_action SET deleted = 1 WHERE id = #{actionId}")
    void logicDeleteById(@Param("actionId") String actionId);
    
    /**
     * 逻辑删除版本下的所有动作
     */
    @Update("UPDATE process_action SET deleted = 1 WHERE version_id = #{versionId}")
    void logicDeleteByVersionId(@Param("versionId") String versionId);
}
