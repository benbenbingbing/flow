package com.workflow.process.form.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.form.infrastructure.persistence.record.FormFieldConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 表单字段配置 Mapper
 */
@Mapper
public interface FormFieldConfigMapper extends BaseMapper<FormFieldConfig> {

    /**
     * 根据表单配置ID查询字段列表
     *
     * @param formConfigId 表单配置ID，后续用于查询表单配置ID时定位或关联目标
     * @return 表单字段配置集合，供调用方遍历或展示
     */
    default List<FormFieldConfig> findByFormConfigId(String formConfigId) {
        return selectList(Wrappers.<FormFieldConfig>lambdaQuery()
                .eq(FormFieldConfig::getFormConfigId, formConfigId)
                .orderByAsc(FormFieldConfig::getSortOrder));
    }
}
