package com.workflow.entity.form.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.form.infrastructure.persistence.record.FormFieldConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 表单字段配置 Mapper
 */
@Mapper
public interface FormFieldConfigMapper extends BaseMapper<FormFieldConfig> {

    /**
     * 根据表单配置ID查询字段列表
     */
    default List<FormFieldConfig> findByFormConfigId(String formConfigId) {
        return selectList(Wrappers.<FormFieldConfig>lambdaQuery()
                .eq(FormFieldConfig::getFormConfigId, formConfigId)
                .orderByAsc(FormFieldConfig::getSortOrder));
    }
}
