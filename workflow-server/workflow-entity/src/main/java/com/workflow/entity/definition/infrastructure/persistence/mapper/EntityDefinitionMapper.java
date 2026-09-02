package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 只更新实体定义中允许通用编辑的列。
     *
     * <p>流程绑定列故意不在该语句中；绑定只能由持有实体/流程锁并处理唯一
     * 约束冲突的专用领域接口修改，避免普通编辑基于旧快照覆盖绑定。</p>
     */
    @Update("UPDATE entity_definition SET "
            + "entity_name = #{entity.entityName}, "
            + "description = #{entity.description}, "
            + "lifecycle_mode = #{entity.lifecycleMode}, "
            + "storage_mode = #{entity.storageMode}, "
            + "team_visibility_enabled = #{entity.teamVisibilityEnabled}, "
            + "team_visibility_level = #{entity.teamVisibilityLevel}, "
            + "update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{entity.id} AND deleted = 0")
    int updateMutableColumns(@Param("entity") EntityDefinition entity);

    /**
     * 字段定义发生变化时仅刷新实体更新时间，禁止把调用方持有的实体快照整行写回。
     */
    @Update("UPDATE entity_definition SET update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int touchUpdateTime(@Param("id") String id);

    /**
     * 迁移回滚仅改变实体启停状态，避免旧实体快照覆盖流程绑定等并发更新。
     */
    @Update("UPDATE entity_definition SET status = #{status}, "
            + "update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatus(
            @Param("id") String id,
            @Param("status") EntityDefinition.Status status);

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
     * 根据流程定义 ID 查询全部活动绑定。
     *
     * <p>本次先以应用锁完成 expand 阶段，不提前在滚动升级中建立唯一索引；
     * 因此调用方必须检查返回基数并对历史重复数据失败关闭。</p>
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition "
            + "WHERE active_process_definition_key = CAST(COALESCE(NULLIF("
            + "TRIM(LEADING '0' FROM TRIM(#{processDefinitionId})), ''), '0') AS UNSIGNED) "
            + "ORDER BY id")
    List<EntityDefinition> findAllByProcessDefinitionId(
            @Param("processDefinitionId") String processDefinitionId);

    /**
     * 以当前读锁定流程绑定对应的实体，避免并发绑定依赖事务快照读到旧结果。
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition "
            + "WHERE active_process_definition_key = CAST(COALESCE(NULLIF("
            + "TRIM(LEADING '0' FROM TRIM(#{processDefinitionId})), ''), '0') AS UNSIGNED) "
            + "ORDER BY id FOR UPDATE")
    List<EntityDefinition> findAllByProcessDefinitionIdForUpdate(
            @Param("processDefinitionId") String processDefinitionId);
}
