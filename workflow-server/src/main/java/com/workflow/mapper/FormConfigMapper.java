package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FormConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 表单配置 Mapper
 */
@Mapper
public interface FormConfigMapper extends BaseMapper<FormConfig> {

    /**
     * 根据节点配置ID查询表单列表
     */
    @Select("SELECT * FROM form_config WHERE node_config_id = #{nodeConfigId} ORDER BY created_at ASC")
    List<FormConfig> findByNodeConfigId(@Param("nodeConfigId") String nodeConfigId);
}
