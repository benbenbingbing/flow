package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
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

    String SELECT_COLUMNS = "id, entity_code, entity_name, description, "
            + "table_name AS physical_table_name, process_definition_id, "
            + "lifecycle_mode, storage_mode, team_visibility_enabled, team_visibility_level, "
            + "status, create_time, update_time, created_by";

    /**
     * 根据编码查询
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition WHERE entity_code = #{code}")
    Optional<EntityDefinition> findByEntityCode(@Param("code") String code);

    /**
     * Lock one entity definition for a serialized publish transaction.
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition "
            + "WHERE entity_code = #{code} FOR UPDATE")
    Optional<EntityDefinition> findByEntityCodeForUpdate(
            @Param("code") String code);

    /**
     * 以共享锁读取实体定义。普通记录写可并发持有该锁，但会与实体发布的
     * 独占锁互斥，确保关系快照在整次记录写入期间不切换。
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition "
            + "WHERE entity_code = #{code} FOR SHARE")
    Optional<EntityDefinition> findByEntityCodeForShare(
            @Param("code") String code);

    /** 发布事务按主键锁定实体定义，统一与记录写的共享锁序。 */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition WHERE id = #{id} FOR UPDATE")
    Optional<EntityDefinition> findByIdForUpdate(
            @Param("id") String id);

    /**
     * 查询所有实体及其字段
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition ORDER BY create_time DESC")
    List<EntityDefinition> findAllWithFields();

    /**
     * 查询所有实体编码
     */
    @Select("SELECT entity_code FROM entity_definition WHERE entity_code IS NOT NULL AND entity_code <> ''")
    List<String> findAllEntityCodes();

    /**
     * 根据流程定义ID查询绑定的实体
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition "
            + "WHERE process_definition_id = #{processDefinitionId} LIMIT 1")
    Optional<EntityDefinition> findByProcessDefinitionId(@Param("processDefinitionId") String processDefinitionId);
}
