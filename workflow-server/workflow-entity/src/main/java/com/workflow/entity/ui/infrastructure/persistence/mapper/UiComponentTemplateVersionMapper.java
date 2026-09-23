package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplateVersion;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * UI 组件模板版本 Mapper
 *
 * 提供按模板 ID 查询历史版本列表的能力。
 */
@Mapper
public interface UiComponentTemplateVersionMapper extends BaseMapper<UiComponentTemplateVersion> {

    /**
     * 根据模板 ID 查询版本列表，按版本号降序排列。
     *
     * @param templateId 模板 ID
     * @return 版本列表
     */
    default List<UiComponentTemplateVersion> findByTemplateId(String templateId) {
        return selectList(Wrappers.<UiComponentTemplateVersion>lambdaQuery()
                .eq(UiComponentTemplateVersion::getTemplateId, templateId)
                .orderByDesc(UiComponentTemplateVersion::getVersion));
    }
}
