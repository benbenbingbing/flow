package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiDataSourceDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * UI 数据源定义 Mapper
 * 
 * UI 数据源（如外部接口、静态枚举等）的元数据定义持久化接口，
 * 目前仅继承通用 CRUD 能力，暂无自定义方法。
 */
@Mapper
public interface UiDataSourceDefinitionMapper extends BaseMapper<UiDataSourceDefinition> {
}
