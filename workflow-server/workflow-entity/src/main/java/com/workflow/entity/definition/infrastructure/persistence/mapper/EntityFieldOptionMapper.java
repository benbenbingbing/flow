package com.workflow.entity.definition.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 仅随实体字段删除清理历史选项；不再提供内嵌选项查询、插入或更新接口。 */
@Mapper
public interface EntityFieldOptionMapper {
    @Delete("DELETE FROM entity_field_option WHERE field_id = #{fieldId}")
    void deleteByFieldId(@Param("fieldId") String fieldId);
}
