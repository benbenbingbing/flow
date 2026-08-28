package com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 表单唯一值稳定字段作用域的事务锁 Mapper。 */
@Mapper
public interface EntityFormUniqueValueGateMapper {

    @Insert("INSERT IGNORE INTO entity_form_unique_value_gate "
            + "(scope_key, value_hash) VALUES (#{scopeKey}, #{valueHash})")
    int insertIgnore(
            @Param("scopeKey") String scopeKey,
            @Param("valueHash") String valueHash);

    /**
     * 在当前事务内锁定指定值门闩。调用前必须先 insertIgnore，
     * 使首次出现的值也有可锁定的稳定行。
     */
    @Select("SELECT value_hash FROM entity_form_unique_value_gate "
            + "WHERE scope_key = #{scopeKey} AND value_hash = #{valueHash} "
            + "FOR UPDATE")
    String lockForUpdate(
            @Param("scopeKey") String scopeKey,
            @Param("valueHash") String valueHash);
}
