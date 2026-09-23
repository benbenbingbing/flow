package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
// LIKE 搜索模式由 Wrapper 绑定参数，保留已有通配符语义。
@Mapper
public interface UiConfigReleaseMapper extends BaseMapper<UiConfigRelease> {

    /**
     * 查询快照中可能引用指定接口服务的当前激活版本。
     *
     * <p>LIKE 只缩小候选集，调用方必须在完整性校验后解析快照并精确匹配
     * serviceId。该查询使已从草稿删除、但仍在线上的 PUBLISHED_ONLY 引用
     * 可以被管理端发现。</p>
     *
     * @param compactNeedle {@code compact}{@code needle}，作为 {@code query.like} 的输入影响后续处理
     * @param spacedNeedle {@code spaced}{@code needle}，供本方法查询活动引用候选集合时使用
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    default List<UiConfigRelease> findActiveReferenceCandidates(String compactNeedle, String spacedNeedle) {
        // 原 SQL 的 LIKE NULL 不匹配任何行，不能让 Wrapper 将 null 拼成字符串 "null"。
        if (compactNeedle == null && spacedNeedle == null) {
            return List.of();
        }
        return selectList(Wrappers.<UiConfigRelease>lambdaQuery()
                .eq(UiConfigRelease::getStatus, "ACTIVE")
                .in(UiConfigRelease::getConfigType, "FORM", "LIST")
                .isNotNull(UiConfigRelease::getSnapshotDocument)
                .and(query -> {
                    if (compactNeedle != null) {
                        query.like(UiConfigRelease::getSnapshotDocument, compactNeedle);
                    }
                    if (spacedNeedle != null) {
                        query.or(compactNeedle != null).like(UiConfigRelease::getSnapshotDocument, spacedNeedle);
                    }
                })
                .orderByAsc(UiConfigRelease::getConfigType, UiConfigRelease::getConfigId)
                .orderByDesc(UiConfigRelease::getVersion));
    }

    /**
     * 查询可能引用接口服务的全部 FORM/LIST 发布版本，供删除保护使用。
     * 历史版本仍可能被签名运行上下文或 Embed 固定；LIKE 只按原始 ID 缩小
     * 候选，以覆盖嵌套 JSON 字符串中的转义键，调用方仍须递归精确解析。
     *
     * @param serviceId 服务ID，后续用于查询{@code executable}数据来源引用候选集合时定位或关联目标
     * @return 界面配置发布版本集合，供调用方遍历或展示
     */
    default List<UiConfigRelease> findExecutableDataSourceReferenceCandidates(String serviceId) {
        if (serviceId == null) {
            return List.of();
        }
        return selectList(Wrappers.<UiConfigRelease>lambdaQuery()
                .in(UiConfigRelease::getConfigType, "FORM", "LIST")
                .isNotNull(UiConfigRelease::getSnapshotDocument)
                .like(UiConfigRelease::getSnapshotDocument, serviceId)
                .orderByAsc(UiConfigRelease::getConfigType, UiConfigRelease::getConfigId)
                .orderByDesc(UiConfigRelease::getVersion));
    }

    /**
     * 根据配置类型与配置 ID 查询全部发布版本，按版本号降序排列。
     *
     * @param configType 配置类型
     * @param configId   配置 ID
     * @return 发布版本列表
     */
    default List<UiConfigRelease> findReleases(String configType, String configId) {
        return selectList(Wrappers.<UiConfigRelease>lambdaQuery()
                .eq(UiConfigRelease::getConfigType, configType)
                .eq(UiConfigRelease::getConfigId, configId)
                .orderByDesc(UiConfigRelease::getVersion));
    }

    /**
     * 分页查询发布历史摘要，并关联人员目录回显发布人的姓名与登录名。
     * 发布记录仅保存人员 ID，历史页不能将该内部 ID 直接暴露为发布人名称。
     *
     * @param configType 配置类型标识，决定后续发布版本{@code summaries}采用的处理分支
     * @param configId 配置ID，后续用于查询发布版本{@code summaries}时定位或关联目标
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 界面配置发布版本摘要集合，供调用方遍历或展示
     */
    default List<UiConfigReleaseSummaryDTO> findReleaseSummaries(String configType, String configId, long offset, int pageSize) {
        return findReleaseSummariesRows(new OffsetPage<>(offset, pageSize), configType, configId);
    }

    /**
     * 复杂查询保留业务 SQL，行范围由 MyBatis-Plus 分页插件生成。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param configType 配置类型标识，决定后续发布版本{@code summaries}行采用的处理分支
     * @param configId 配置ID，后续用于查询发布版本{@code summaries}行时定位或关联目标
     * @return 界面配置发布版本摘要集合，供调用方遍历或展示
     */
    @Select("<script> SELECT r.id, r.config_type, r.config_id, r.version, r.content_hash, "
            + "r.status, r.description, r.release_mode, r.base_release_id, "
            + "r.risk_level, r.rollout_scope, r.published_by, "
            + "u.nickname AS published_by_name, "
            + "u.username AS published_by_username, r.published_at, "
            + "CASE WHEN r.release_mode &lt;> 'HOTFIX' THEN NULL "
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
            + "ORDER BY r.version DESC  </script>")
    List<UiConfigReleaseSummaryDTO> findReleaseSummariesRows(
            @Param("page") com.baomidou.mybatisplus.core.metadata.IPage<?> page,
            @Param("configType") String configType,
            @Param("configId") String configId);

    /**
     * 统计指定配置的发布记录数，供发布历史分页使用。
     *
     * @param configType 配置类型标识，决定后续{@code releases}采用的处理分支
     * @param configId 配置ID，后续用于统计{@code releases}时定位或关联目标
     * @return 符合条件的{@code releases}数量
     */
    default long countReleases(String configType, String configId) {
        return selectCount(Wrappers.<UiConfigRelease>lambdaQuery()
                .eq(UiConfigRelease::getConfigType, configType)
                .eq(UiConfigRelease::getConfigId, configId));
    }

    /**
     * 查询最大版本；查询结果供调用方展示或继续处理。
     *
     * @param configType 配置类型标识，决定后续最大版本采用的处理分支
     * @param configId 配置ID，后续用于查询最大版本时定位或关联目标
     * @return 符合条件的界面配置发布版本结果，供调用方继续处理
     */
    default int findMaxVersion(String configType, String configId) {
        List<Object> values = selectObjs(Wrappers.<UiConfigRelease>query()
                .select("MAX(version)")
                .eq("config_type", configType)
                .eq("config_id", configId));
        return values.isEmpty() || values.get(0) == null ? 0 : ((Number) values.get(0)).intValue();
    }

    /**
     * 按版本查询界面配置发布版本；结果供后续展示或处理。
     *
     * @param configType 配置类型标识，决定后续版本采用的处理分支
     * @param configId 配置ID，后续用于查询版本时定位或关联目标
     * @param version 版本，作为 {@code eq} 的输入影响后续处理
     * @return 符合条件的界面配置发布版本结果，供调用方继续处理
     */
    default UiConfigRelease findByVersion(String configType, String configId, Integer version) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<UiConfigRelease>lambdaQuery()
                .eq(UiConfigRelease::getConfigType, configType)
                .eq(UiConfigRelease::getConfigId, configId)
                .eq(UiConfigRelease::getVersion, version))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据配置类型与配置 ID 查询当前活跃（ACTIVE）的发布版本，取版本号最大的一条。
     *
     * @param configType 配置类型
     * @param configId   配置 ID
     * @return 活跃发布版本，无则返回 null
     */
    default UiConfigRelease findActive(String configType, String configId) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<UiConfigRelease>lambdaQuery()
                .eq(UiConfigRelease::getConfigType, configType)
                .eq(UiConfigRelease::getConfigId, configId)
                .eq(UiConfigRelease::getStatus, "ACTIVE")
                .orderByDesc(UiConfigRelease::getVersion))
                .stream().findFirst().orElse(null);
    }
}
