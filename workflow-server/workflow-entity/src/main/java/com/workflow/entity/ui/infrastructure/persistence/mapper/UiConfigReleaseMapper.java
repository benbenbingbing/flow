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
     * 查询快照中可能引用指定接口服务的当前激活版本。
     *
     * <p>LIKE 只缩小候选集，调用方必须在完整性校验后解析快照并精确匹配
     * serviceId。该查询使已从草稿删除、但仍在线上的 PUBLISHED_ONLY 引用
     * 可以被管理端发现。</p>
     */
    @Select("SELECT * FROM ui_config_release "
            + "WHERE status = 'ACTIVE' "
            + "AND config_type IN ('FORM', 'LIST') "
            + "AND snapshot_document IS NOT NULL "
            + "AND (snapshot_document LIKE CONCAT('%', #{compactNeedle}, '%') "
            + "OR snapshot_document LIKE CONCAT('%', #{spacedNeedle}, '%')) "
            + "ORDER BY config_type, config_id, version DESC")
    List<UiConfigRelease> findActiveReferenceCandidates(
            @Param("compactNeedle") String compactNeedle,
            @Param("spacedNeedle") String spacedNeedle);

    /**
     * 查询可能引用接口服务的全部 FORM/LIST 发布版本，供删除保护使用。
     * 历史版本仍可能被签名运行上下文或 Embed 固定；LIKE 只按原始 ID 缩小
     * 候选，以覆盖嵌套 JSON 字符串中的转义键，调用方仍须递归精确解析。
     */
    @Select("SELECT * FROM ui_config_release "
            + "WHERE config_type IN ('FORM', 'LIST') "
            + "AND snapshot_document IS NOT NULL "
            + "AND snapshot_document LIKE CONCAT('%', #{serviceId}, '%') "
            + "ORDER BY config_type, config_id, version DESC")
    List<UiConfigRelease> findExecutableDataSourceReferenceCandidates(
            @Param("serviceId") String serviceId);

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

    /**
     * 分页查询发布历史摘要，并关联人员目录回显发布人的姓名与登录名。
     * 发布记录仅保存人员 ID，历史页不能将该内部 ID 直接暴露为发布人名称。
     */
    @Select("SELECT r.id, r.config_type, r.config_id, r.version, r.content_hash, "
            + "r.status, r.description, r.release_mode, r.base_release_id, "
            + "r.risk_level, r.rollout_scope, r.published_by, "
            + "u.nickname AS published_by_name, "
            + "u.username AS published_by_username, r.published_at, "
            + "CASE WHEN r.release_mode <> 'HOTFIX' THEN NULL "
            + "WHEN EXISTS (SELECT 1 FROM ui_config_hotfix_target t "
            + "WHERE t.hotfix_release_id = r.id "
            + "AND t.status = 'ACTIVE') THEN 'ACTIVE' "
            + "WHEN EXISTS (SELECT 1 FROM ui_config_hotfix_target t "
            + "WHERE t.hotfix_release_id = r.id "
            + "AND t.status = 'SUPERSEDED') THEN 'SUPERSEDED' "
            + "WHEN EXISTS (SELECT 1 FROM ui_config_hotfix_target t "
            + "WHERE t.hotfix_release_id = r.id "
            + "AND t.status = 'ROLLED_BACK') "
            + "OR EXISTS (SELECT 1 FROM ui_config_release_audit a "
            + "WHERE a.release_id = r.id "
            + "AND a.operation = 'ROLLBACK_HOTFIX') THEN 'ROLLED_BACK' "
            + "WHEN r.status = 'ACTIVE' THEN 'ACTIVE' "
            + "ELSE 'SUPERSEDED' END AS rollout_status "
            + "FROM ui_config_release r "
            + "LEFT JOIN sys_user u ON u.id = r.published_by AND u.deleted = 0 "
            + "WHERE r.config_type = #{configType} AND r.config_id = #{configId} "
            + "ORDER BY r.version DESC LIMIT #{offset}, #{pageSize}")
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
