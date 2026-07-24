package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiExtensionDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * UI 扩展定义 Mapper
 * 
 * UI 扩展组件（自定义组件/查询提供者等）的元数据定义持久化接口，
 * 目前仅继承通用 CRUD 能力，暂无自定义方法。
 */
@Mapper
public interface UiExtensionDefinitionMapper
        extends BaseMapper<UiExtensionDefinition> {
}
