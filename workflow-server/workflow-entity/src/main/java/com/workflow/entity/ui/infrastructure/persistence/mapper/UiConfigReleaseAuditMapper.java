package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigReleaseAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * UI 发布审计 Mapper。
 */
@Mapper
public interface UiConfigReleaseAuditMapper
        extends BaseMapper<UiConfigReleaseAudit> {
}
