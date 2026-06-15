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
    @Select("SELECT * FROM entity_definition ORDER BY create_time DESC")
    List<EntityDefinition> findAllWithFields();

    /**
     * 查询所有实体编码
     */
    @Select("SELECT entity_code FROM entity_definition WHERE entity_code IS NOT NULL AND entity_code <> ''")
    List<String> findAllEntityCodes();

    /**
     * 根据流程定义ID查询绑定的实体
     */
    @Select("SELECT * FROM entity_definition WHERE process_definition_id = #{processDefinitionId} LIMIT 1")
    Optional<EntityDefinition> findByProcessDefinitionId(@Param("processDefinitionId") String processDefinitionId);
}
