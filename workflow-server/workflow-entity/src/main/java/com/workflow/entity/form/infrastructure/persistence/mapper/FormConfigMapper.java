package com.workflow.entity.form.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.form.infrastructure.persistence.record.FormConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 表单配置 Mapper
 */
@Mapper
public interface FormConfigMapper extends BaseMapper<FormConfig> {

    /**
     * 根据节点配置ID查询表单列表
     */
    default List<FormConfig> findByNodeConfigId(String nodeConfigId) {
        return selectList(Wrappers.<FormConfig>lambdaQuery()
                .eq(FormConfig::getNodeConfigId, nodeConfigId)
                .orderByAsc(FormConfig::getCreatedAt));
    }
}
