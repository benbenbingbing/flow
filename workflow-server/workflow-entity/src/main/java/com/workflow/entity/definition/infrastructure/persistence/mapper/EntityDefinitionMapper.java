package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;

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
     *
     * @param code 编码，后续用于查询实体编码时定位或关联目标
     * @return 匹配的实体编码；未找到时为空
     */
    default Optional<EntityDefinition> findByEntityCode(String code) {
        return Optional.ofNullable(selectOne(Wrappers.<EntityDefinition>lambdaQuery()
                .eq(EntityDefinition::getEntityCode, code)));
    }

    /**
     * Lock one entity definition for a serialized publish transaction.
     *
     * @param code 编码，后续用于查询实体编码更新时定位或关联目标
     * @return 匹配的实体编码更新；未找到时为空
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition "
            + "WHERE entity_code = #{code} FOR UPDATE")
    Optional<EntityDefinition> findByEntityCodeForUpdate(
            @Param("code") String code);

    /**
     * 读取并保护实体定义，与发布的独占锁互斥，确保整次记录写入期间关系快照不切换。
     * 支持行共享锁的产品允许普通写并发持有；Oracle/DM 等采用独占行锁保护，可能串行等待。
     *
     * @param code 编码，后续用于查询实体编码{@code share}时定位或关联目标
     * @return 匹配的实体编码{@code share}；未找到时为空
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("<script>SELECT " + SELECT_COLUMNS
            + " FROM entity_definition "
            + "WHERE entity_code = #{code} ${@com.workflow.integration.database.api.query.DatabaseQuerySql@readGuard(_databaseId)}</script>")
    Optional<EntityDefinition> findByEntityCodeForShare(
            @Param("code") String code);

    /**
     * 发布事务按主键锁定实体定义，统一与记录写的共享锁序。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 匹配的ID更新；未找到时为空
     */
    @Select("SELECT " + SELECT_COLUMNS
            + " FROM entity_definition WHERE id = #{id} FOR UPDATE")
    Optional<EntityDefinition> findByIdForUpdate(
            @Param("id") String id);

    /**
     * 只更新实体定义中允许通用编辑的列。
     *
     * <p>流程绑定列故意不在该语句中；绑定只能由持有实体/流程锁并处理唯一
     * 约束冲突的专用领域接口修改，避免普通编辑基于旧快照覆盖绑定。</p>
     *
     * @param entity 实体，作为 {@code set} 的输入影响后续处理
     * @return 更新后的可变列集合结果，供调用方继续处理
     */
    default int updateMutableColumns(EntityDefinition entity) {
        // 显式 SET 保留传入 NULL 的清空语义，同时避免整行写回覆盖流程绑定。
        return update(null, Wrappers.<EntityDefinition>lambdaUpdate()
                .eq(EntityDefinition::getId, entity.getId())
                .apply("deleted = 0")
                .set(EntityDefinition::getEntityName, entity.getEntityName())
                .set(EntityDefinition::getDescription, entity.getDescription())
                .set(EntityDefinition::getLifecycleMode, entity.getLifecycleMode())
                .set(EntityDefinition::getStorageMode, entity.getStorageMode())
                .set(EntityDefinition::getTeamVisibilityEnabled, entity.getTeamVisibilityEnabled())
                .set(EntityDefinition::getTeamVisibilityLevel, entity.getTeamVisibilityLevel())
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }

    /**
     * 字段定义发生变化时仅刷新实体更新时间，禁止把调用方持有的实体快照整行写回。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的更新访问时间更新时间结果，供调用方继续处理
     */
    default int touchUpdateTime(String id) {
        return update(null, Wrappers.<EntityDefinition>lambdaUpdate()
                .eq(EntityDefinition::getId, id)
                .apply("deleted = 0")
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }

    /**
     * 迁移回滚仅改变实体启停状态，避免旧实体快照覆盖流程绑定等并发更新。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @return 更新后的状态结果，供调用方继续处理
     */
    default int updateStatus(String id, EntityDefinition.Status status) {
        return update(null, Wrappers.<EntityDefinition>lambdaUpdate()
                .eq(EntityDefinition::getId, id)
                .apply("deleted = 0")
                .set(EntityDefinition::getStatus, status)
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }

    /**
     * 查询所有实体及其字段
     *
     * @return 实体定义集合，供调用方遍历或展示
     */
    default List<EntityDefinition> findAllWithFields() {
        return selectList(Wrappers.<EntityDefinition>lambdaQuery()
                .orderByDesc(EntityDefinition::getCreatedAt, EntityDefinition::getId));
    }

    /**
     * 查询所有非空实体编码；NULLIF 保留字符列的比较规则，并兼容空串即 NULL 的数据库。
     *
     * @return 实体定义集合，供调用方遍历或展示
     */
    default List<String> findAllEntityCodes() {
        return selectList(Wrappers.<EntityDefinition>lambdaQuery()
                .select(EntityDefinition::getEntityCode)
                .apply("NULLIF(entity_code, '') IS NOT NULL"))
                .stream().map(EntityDefinition::getEntityCode).toList();
    }

    /**
     * 根据流程定义 ID 查询全部活动绑定。
     * 编号先规范为正数 BIGINT 再绑定，非法编号无匹配；保留活动绑定索引的等值访问。
     *
     * <p>本次先以应用锁完成 expand 阶段，不提前在滚动升级中建立唯一索引；
     * 因此调用方必须检查返回基数并对历史重复数据失败关闭。</p>
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 实体定义集合，供调用方遍历或展示
     */
    default List<EntityDefinition> findAllByProcessDefinitionId(String processDefinitionId) {
        Long processDefinitionKey = com.workflow.entity.definition.infrastructure.persistence.ProcessDefinitionBindingKey
                .parse(processDefinitionId);
        return selectList(Wrappers.<EntityDefinition>query()
                .eq("active_process_definition_key", processDefinitionKey)
                .orderByAsc("id"));
    }

    /**
     * 以当前读锁定流程绑定对应的实体，避免并发绑定依赖事务快照读到旧结果。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 实体定义集合，供调用方遍历或展示
     */
    @Select("<script><bind name=\"processDefinitionKey\" "
            + "value=\"@com.workflow.entity.definition.infrastructure.persistence.ProcessDefinitionBindingKey@parse(processDefinitionId)\"/>"
            + "SELECT " + SELECT_COLUMNS + " FROM entity_definition "
            + "WHERE active_process_definition_key = #{processDefinitionKey,jdbcType=BIGINT} "
            + "ORDER BY id FOR UPDATE</script>")
    List<EntityDefinition> findAllByProcessDefinitionIdForUpdate(
            @Param("processDefinitionId") String processDefinitionId);
}
