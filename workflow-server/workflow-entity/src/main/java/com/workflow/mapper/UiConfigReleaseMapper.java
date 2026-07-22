package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiConfigRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UiConfigReleaseMapper extends BaseMapper<UiConfigRelease> {

    @Select("SELECT * FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "ORDER BY version DESC")
    List<UiConfigRelease> findReleases(
            @Param("configType") String configType,
            @Param("configId") String configId);

    @Select("SELECT * FROM ui_config_release "
            + "WHERE config_type = #{configType} AND config_id = #{configId} "
            + "AND status = 'ACTIVE' ORDER BY version DESC LIMIT 1")
    UiConfigRelease findActive(
            @Param("configType") String configType,
            @Param("configId") String configId);
}
