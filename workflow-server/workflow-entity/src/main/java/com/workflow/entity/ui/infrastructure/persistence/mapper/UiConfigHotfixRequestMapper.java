package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixRequest;
import org.apache.ibatis.annotations.Mapper;

/** UI HOTFIX 发布审计与观察记录 Mapper。 */
@Mapper
public interface UiConfigHotfixRequestMapper extends BaseMapper<UiConfigHotfixRequest> {
}
