package com.workflow.admin.extension.person.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.extension.person.infrastructure.persistence.record.PersonResolverDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人员解析器目录 Mapper。
 */
@Mapper
public interface PersonResolverDefinitionMapper
        extends BaseMapper<PersonResolverDefinition> {
}
