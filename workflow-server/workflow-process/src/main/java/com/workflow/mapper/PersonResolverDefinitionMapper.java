package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.PersonResolverDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人员解析器目录 Mapper。
 */
@Mapper
public interface PersonResolverDefinitionMapper
        extends BaseMapper<PersonResolverDefinition> {
}
