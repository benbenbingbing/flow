package com.workflow.process.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 流程定义配置 Mapper
 */
@Mapper
public interface ProcessDefinitionConfigMapper extends BaseMapper<ProcessDefinitionConfig> {

    /**
     * 按期望修订号原子更新整份流程草稿。
     *
     * <p>更新条件与修订号递增必须由同一条 SQL 完成，避免“先查版本再更新”在并发请求间留下覆盖窗口。</p>
     *
     * @return 1 表示更新成功，0 表示草稿不存在或修订号已经变化
     */
    default int updateDraftCas(String id, long expectedRevision, String processName,
            String description, String category, String bpmnXml, String draftHash) {
        return update(null, Wrappers.<ProcessDefinitionConfig>lambdaUpdate()
                .set(ProcessDefinitionConfig::getProcessName, processName)
                .set(ProcessDefinitionConfig::getDescription, description)
                .set(ProcessDefinitionConfig::getCategory, category)
                .set(ProcessDefinitionConfig::getBpmnXml, bpmnXml)
                .set(ProcessDefinitionConfig::getDraftHash, draftHash)
                // 修订号比较与递增继续在一条语句内完成，不能拆成读取后覆盖写入。
                .setSql("draft_revision = draft_revision + 1")
                .setSql("update_time = CURRENT_TIMESTAMP")
                .eq(ProcessDefinitionConfig::getId, id)
                .eq(ProcessDefinitionConfig::getDraftRevision, expectedRevision));
    }

    /**
     * Lock one process definition for a serialized publish transaction.
     */
    @Select("SELECT * FROM process_definition_config "
            + "WHERE id = #{id} "
            + "AND deleted = 0 FOR UPDATE")
    ProcessDefinitionConfig selectByIdForUpdate(
            @Param("id") String id);

    /**
     * 锁定流程配置的绑定门闩，包括已逻辑删除的配置。
     *
     * <p>已发布版本在配置删除后仍可能运行，因此实体绑定保护不能因 deleted=1
     * 跳过同一行锁。</p>
     */
    @Select("SELECT * FROM process_definition_config "
            + "WHERE id = #{id} FOR UPDATE")
    ProcessDefinitionConfig selectAnyByIdForBindingUpdate(
            @Param("id") String id);

    /**
     * 根据流程标识查询（排除已删除）
     */
    default Optional<ProcessDefinitionConfig> findByProcessKey(String processKey) {
        return Optional.ofNullable(selectOne(Wrappers.<ProcessDefinitionConfig>lambdaQuery()
                .eq(ProcessDefinitionConfig::getProcessKey, processKey)
                .eq(ProcessDefinitionConfig::getDeleted, 0)));
    }

    /**
     * 根据状态查询（排除已删除）
     */
    default List<ProcessDefinitionConfig> findByStatus(String status) {
        return selectList(Wrappers.<ProcessDefinitionConfig>lambdaQuery()
                .eq(ProcessDefinitionConfig::getStatus, status)
                .eq(ProcessDefinitionConfig::getDeleted, 0));
    }

    /** 按指定流程编码读取发布配置；空集合表示没有可选流程，不能省略范围条件。 */
    default List<ProcessDefinitionConfig> findPublishedByKeys(Collection<String> processKeys) {
        if (processKeys == null || processKeys.isEmpty()) {
            return List.of();
        }
        return selectList(Wrappers.<ProcessDefinitionConfig>lambdaQuery()
                .eq(ProcessDefinitionConfig::getStatus, "PUBLISHED")
                .in(ProcessDefinitionConfig::getProcessKey, processKeys)
                .orderByAsc(ProcessDefinitionConfig::getProcessKey));
    }

    /**
     * 检查流程标识是否存在（排除已删除）
     */
    default boolean existsByProcessKey(String processKey) {
        return selectCount(Wrappers.<ProcessDefinitionConfig>lambdaQuery()
                .eq(ProcessDefinitionConfig::getProcessKey, processKey)) > 0;
    }

    /**
     * 查询所有流程（排除已删除）
     */
    default List<ProcessDefinitionConfig> findAllActive() {
        return selectList(Wrappers.<ProcessDefinitionConfig>lambdaQuery()
                .eq(ProcessDefinitionConfig::getDeleted, 0)
                .orderByDesc(ProcessDefinitionConfig::getUpdatedAt));
    }

    /**
     * 查询所有未被实体绑定的流程（排除已删除）
     * 用于实体绑定流程时选择
     */
    @Select("SELECT p.* FROM process_definition_config p " +
            "WHERE p.deleted = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM entity_definition e " +
            "WHERE e.active_process_definition_key = p.id) " +
            "ORDER BY p.update_time DESC")
    List<ProcessDefinitionConfig> findAllUnbound();
}
