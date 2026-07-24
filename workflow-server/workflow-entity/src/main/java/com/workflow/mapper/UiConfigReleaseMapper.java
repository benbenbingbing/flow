package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiConfigRelease;
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
