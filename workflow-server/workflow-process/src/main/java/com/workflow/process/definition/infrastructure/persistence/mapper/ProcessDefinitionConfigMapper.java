package com.workflow.process.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
    @Update("""
            UPDATE process_definition_config
               SET process_name = #{processName},
                   description = #{description},
                   category = #{category},
                   bpmn_xml = #{bpmnXml},
                   draft_revision = draft_revision + 1,
                   draft_hash = #{draftHash},
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND deleted = 0
               AND draft_revision = #{expectedRevision}
            """)
    int updateDraftCas(
            @Param("id") String id,
            @Param("expectedRevision") long expectedRevision,
            @Param("processName") String processName,
            @Param("description") String description,
            @Param("category") String category,
            @Param("bpmnXml") String bpmnXml,
            @Param("draftHash") String draftHash);

    /**
     * Lock one process definition for a serialized publish transaction.
     */
    @Select("SELECT * FROM process_definition_config "
            + "WHERE id = #{id} "
            + "AND deleted = 0 FOR UPDATE")
    ProcessDefinitionConfig selectByIdForUpdate(
            @Param("id") String id);

    /**
     * 根据流程标识查询（排除已删除）
     */
    @Select("SELECT * FROM process_definition_config WHERE process_key = #{processKey} AND deleted = 0")
    Optional<ProcessDefinitionConfig> findByProcessKey(@Param("processKey") String processKey);

    /**
     * 根据状态查询（排除已删除）
     */
    @Select("SELECT * FROM process_definition_config WHERE status = #{status} AND deleted = 0")
    List<ProcessDefinitionConfig> findByStatus(@Param("status") String status);

    @Select("""
            <script>
            SELECT *
              FROM process_definition_config
             WHERE status = 'PUBLISHED'
               AND deleted = 0
               AND process_key IN
               <foreach collection="processKeys" item="processKey"
                        open="(" separator="," close=")">
                 #{processKey}
               </foreach>
             ORDER BY process_key
            </script>
            """)
    List<ProcessDefinitionConfig> findPublishedByKeys(
            @Param("processKeys") Collection<String> processKeys);

    /**
     * 检查流程标识是否存在（排除已删除）
     */
    @Select("SELECT COUNT(*) > 0 FROM process_definition_config WHERE process_key = #{processKey} AND deleted = 0")
    boolean existsByProcessKey(@Param("processKey") String processKey);

    /**
     * 查询所有流程（排除已删除）
     */
    @Select("SELECT * FROM process_definition_config WHERE deleted = 0 ORDER BY update_time DESC")
    List<ProcessDefinitionConfig> findAllActive();

    /**
     * 查询所有未被实体绑定的流程（排除已删除）
     * 用于实体绑定流程时选择
     */
    @Select("SELECT p.* FROM process_definition_config p " +
            "WHERE p.deleted = 0 " +
            "AND p.id NOT IN (SELECT e.process_definition_id FROM entity_definition e WHERE e.process_definition_id IS NOT NULL) " +
            "ORDER BY p.update_time DESC")
    List<ProcessDefinitionConfig> findAllUnbound();
}
