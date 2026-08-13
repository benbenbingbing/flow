package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.api.response.UiConfigReleaseSummaryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * UI 配置发布版本 Mapper
 * 
 * 提供按配置类型与配置 ID 查询发布历史版本及当前活跃版本的能力。
 */
@Mapper
public interface UiConfigReleaseMapper extends BaseMapper<UiConfigRelease> {

    /**
     * 根据配置类型与配置 ID 查询全部发布版本，按版本号降序排列。
     *
     * @param configType 配置类型
     * @param configId   配置 ID
     * @return 发布版本列表
     */
    @Select("SELECT * FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "ORDER BY version DESC")
    List<UiConfigRelease> findReleases(
            @Param("configType") String configType,
            @Param("configId") String configId);

    @Select("SELECT id, config_type, config_id, version, content_hash, "
            + "status, description, release_mode, base_release_id, "
            + "risk_level, rollout_scope, published_by, published_at, "
            + "CASE WHEN release_mode <> 'HOTFIX' THEN NULL "
            + "WHEN EXISTS (SELECT 1 FROM ui_config_hotfix_target t "
            + "WHERE t.hotfix_release_id = ui_config_release.id "
            + "AND t.status = 'ACTIVE') THEN 'ACTIVE' "
            + "WHEN EXISTS (SELECT 1 FROM ui_config_hotfix_target t "
            + "WHERE t.hotfix_release_id = ui_config_release.id "
            + "AND t.status = 'SUPERSEDED') THEN 'SUPERSEDED' "
            + "WHEN EXISTS (SELECT 1 FROM ui_config_hotfix_target t "
            + "WHERE t.hotfix_release_id = ui_config_release.id "
            + "AND t.status = 'ROLLED_BACK') "
            + "OR EXISTS (SELECT 1 FROM ui_config_release_audit a "
            + "WHERE a.release_id = ui_config_release.id "
            + "AND a.operation = 'ROLLBACK_HOTFIX') THEN 'ROLLED_BACK' "
            + "WHEN status = 'ACTIVE' THEN 'ACTIVE' "
            + "ELSE 'SUPERSEDED' END AS rollout_status "
            + "FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "ORDER BY version DESC LIMIT #{offset}, #{pageSize}")
    List<UiConfigReleaseSummaryDTO> findReleaseSummaries(
            @Param("configType") String configType,
            @Param("configId") String configId,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId}")
    long countReleases(
            @Param("configType") String configType,
            @Param("configId") String configId);

    @Select("SELECT COALESCE(MAX(version), 0) FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId}")
    int findMaxVersion(
            @Param("configType") String configType,
            @Param("configId") String configId);

    @Select("SELECT * FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "AND version = #{version} LIMIT 1")
    UiConfigRelease findByVersion(
            @Param("configType") String configType,
            @Param("configId") String configId,
            @Param("version") Integer version);

    /**
     * 根据配置类型与配置 ID 查询当前活跃（ACTIVE）的发布版本，取版本号最大的一条。
     *
     * @param configType 配置类型
     * @param configId   配置 ID
     * @return 活跃发布版本，无则返回 null
     */
    @Select("SELECT * FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "AND status = 'ACTIVE' ORDER BY version DESC LIMIT 1")
    UiConfigRelease findActive(
            @Param("configType") String configType,
            @Param("configId") String configId);
}
