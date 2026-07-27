package com.workflow.migration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigEnvironmentMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置环境映射 Mapper。
 *
 * <p>提供 config_environment_mapping 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigEnvironmentMappingMapper extends BaseMapper<ConfigEnvironmentMapping> {
}
