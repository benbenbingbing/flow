package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiConfigHotfixTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * UI 热修复目标 Mapper。
 */
@Mapper
public interface UiConfigHotfixTargetMapper
        extends BaseMapper<UiConfigHotfixTarget> {

    @Select("SELECT * FROM ui_config_hotfix_target "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "AND process_version_history_id = #{processVersionHistoryId} "
            + "AND status = 'ACTIVE' LIMIT 1")
    UiConfigHotfixTarget findActiveTarget(
            @Param("configType") String configType,
            @Param("configId") String configId,
            @Param("processVersionHistoryId") String processVersionHistoryId);

    @Select("SELECT * FROM ui_config_hotfix_target "
            + "WHERE hotfix_release_id = #{hotfixReleaseId} "
            + "ORDER BY activated_at DESC")
    List<UiConfigHotfixTarget> findByHotfixReleaseId(
            @Param("hotfixReleaseId") String hotfixReleaseId);

    @Select("SELECT * FROM ui_config_hotfix_target "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "AND status = 'ACTIVE' ORDER BY activated_at DESC")
    List<UiConfigHotfixTarget> findActiveByConfig(
            @Param("configType") String configType,
            @Param("configId") String configId);
}
