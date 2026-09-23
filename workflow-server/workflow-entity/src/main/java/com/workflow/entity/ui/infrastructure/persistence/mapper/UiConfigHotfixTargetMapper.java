package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixTarget;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * UI 热修复目标 Mapper。
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface UiConfigHotfixTargetMapper
        extends BaseMapper<UiConfigHotfixTarget> {

    /**
     * 查询活动目标；查询结果供调用方展示或继续处理。
     *
     * @param configType 配置类型标识，决定后续活动目标采用的处理分支
     * @param configId 配置ID，后续用于查询活动目标时定位或关联目标
     * @param processVersionHistoryId 流程版本历史ID，后续用于查询活动目标时定位或关联目标
     * @return 符合条件的界面配置热修复目标结果，供调用方继续处理
     */
    default UiConfigHotfixTarget findActiveTarget(String configType, String configId, String processVersionHistoryId) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<UiConfigHotfixTarget>lambdaQuery()
                .eq(UiConfigHotfixTarget::getConfigType, configType)
                .eq(UiConfigHotfixTarget::getConfigId, configId)
                .eq(UiConfigHotfixTarget::getProcessVersionHistoryId, processVersionHistoryId)
                .eq(UiConfigHotfixTarget::getStatus, "ACTIVE"))
                .stream().findFirst().orElse(null);
    }

    /**
     * 查询一次热修复发布的全部目标，按激活时间倒序返回。
     *
     * @param hotfixReleaseId 热修复发布版本ID，后续用于查询热修复发布版本ID时定位或关联目标
     * @return 界面配置热修复目标集合，供调用方遍历或展示
     */
    default List<UiConfigHotfixTarget> findByHotfixReleaseId(String hotfixReleaseId) {
        return selectList(Wrappers.<UiConfigHotfixTarget>lambdaQuery()
                .eq(UiConfigHotfixTarget::getHotfixReleaseId, hotfixReleaseId)
                .orderByDesc(UiConfigHotfixTarget::getActivatedAt));
    }

    /**
     * 查询指定配置当前生效的热修复目标，供运行时叠加配置使用。
     *
     * @param configType 配置类型标识，决定后续活动配置采用的处理分支
     * @param configId 配置ID，后续用于查询活动配置时定位或关联目标
     * @return 界面配置热修复目标集合，供调用方遍历或展示
     */
    default List<UiConfigHotfixTarget> findActiveByConfig(String configType, String configId) {
        return selectList(Wrappers.<UiConfigHotfixTarget>lambdaQuery()
                .eq(UiConfigHotfixTarget::getConfigType, configType)
                .eq(UiConfigHotfixTarget::getConfigId, configId)
                .eq(UiConfigHotfixTarget::getStatus, "ACTIVE")
                .orderByDesc(UiConfigHotfixTarget::getActivatedAt));
    }
}
