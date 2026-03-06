package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 实体定义 Mapper
 */
@Mapper
public interface EntityDefinitionMapper extends BaseMapper<EntityDefinition> {

    /**
     * 根据编码查询
     */
    @Select("SELECT * FROM entity_definition WHERE entity_code = #{code}")
    Optional<EntityDefinition> findByEntityCode(@Param("code") String code);

    /**
     * 查询所有实体及其字段
     */
    @Select("SELECT * FROM entity_definition ORDER BY created_at DESC")
    List<EntityDefinition> findAllWithFields();
}
