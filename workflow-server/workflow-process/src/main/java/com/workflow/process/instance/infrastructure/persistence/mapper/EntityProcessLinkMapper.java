package com.workflow.process.instance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseSort;

import java.util.List;

// 普通外层分页由 MyBatis-Plus 处理；锁定与嵌套分页仍保留必要的数据库适配。
@Mapper
public interface EntityProcessLinkMapper extends BaseMapper<EntityProcessLink> {

    // JDBC 方言执行器写入后不能复用 MyBatis 的旧缓存，两种锁定读取都必须访问数据库。
    @Select("""
            SELECT * FROM entity_process_link
            WHERE entity_code = #{entityCode}
              AND entity_record_id = #{entityRecordId}
              AND generation = #{generation}
            FOR UPDATE
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    EntityProcessLink selectForUpdate(
            @Param("entityCode") String entityCode,
            @Param("entityRecordId") String entityRecordId,
            @Param("generation") int generation);

    @SelectProvider(type = LockingSql.class, method = "latest")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    EntityProcessLink selectLatestForUpdate(
            @Param("entityCode") String entityCode,
            @Param("entityRecordId") String entityRecordId);

    /** 调用方已串行化实体写入；缺失代次的排他性最终依靠唯一键占用，不依赖 gap lock。 */
    class LockingSql {
        public static String latest(ProviderContext context) {
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).firstForUpdate(
                    "entity_process_link", "entity_code = #{entityCode} AND entity_record_id = #{entityRecordId}",
                    List.of(new DatabaseSort("generation", true), new DatabaseSort("id", true)), "id");
        }
    }

    /**
     * 读取实体记录最新一代流程链接，仅用于影响预览与状态校验。
     *
     * <p>真正发起流程仍使用 {@link #selectLatestForUpdate(String, String)}
     * 串行化 generation；这里不得为了只读预览长时间持有行锁。</p>
     */
    default EntityProcessLink selectLatest(String entityCode, String entityRecordId) {
        return selectList(new Page<EntityProcessLink>(1, 1, false), Wrappers.<EntityProcessLink>lambdaQuery()
                .eq(EntityProcessLink::getEntityCode, entityCode)
                .eq(EntityProcessLink::getEntityRecordId, entityRecordId)
                .orderByDesc(EntityProcessLink::getGeneration)).stream().findFirst().orElse(null);
    }

    @Update("""
            <script>
            UPDATE entity_process_link
            SET process_instance_id = #{processInstanceId},
                state = 'ACTIVE',
                version = version + 1,
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id}
              AND request_id = #{requestId}
              AND state = 'PENDING'
            </script>
            """)
    int activate(
            @Param("id") String id,
            @Param("requestId") String requestId,
            @Param("processInstanceId") String processInstanceId);

    @Update("""
            <script>
            UPDATE entity_process_link
            SET state = 'ENDED',
                entity_status = COALESCE(#{entityStatus,jdbcType=VARCHAR}, entity_status),
                ended_at = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                version = version + 1,
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE process_instance_id = #{processInstanceId}
              AND state = 'ACTIVE'
            </script>
            """)
    int closeActive(
            @Param("processInstanceId") String processInstanceId,
            @Param("entityStatus") String entityStatus);

    /** 在关联关闭事务中独立记录真实结束原因，生命周期仍统一为 ENDED。 */
    default int recordEndType(String instanceId, String endType) {
        return update(null, Wrappers.<EntityProcessLink>lambdaUpdate()
                .set(EntityProcessLink::getEndType, endType)
                .eq(EntityProcessLink::getProcessInstanceId, instanceId));
    }

    @Update("""
            <script>
            UPDATE entity_process_link
            SET entity_status = #{entityStatus,jdbcType=VARCHAR},
                version = version + 1,
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE process_instance_id = #{processInstanceId}
              AND state = 'ACTIVE'
            </script>
            """)
    int updateActiveStatus(
            @Param("processInstanceId") String processInstanceId,
            @Param("entityStatus") String entityStatus);

    /** 按流程实例读取关联记录，首行限制由框架分页方言处理。 */
    default EntityProcessLink findByProcessInstanceId(String processInstanceId) {
        return selectList(new Page<EntityProcessLink>(1, 1, false), Wrappers.<EntityProcessLink>lambdaQuery()
                .eq(EntityProcessLink::getProcessInstanceId, processInstanceId)).stream().findFirst().orElse(null);
    }

    default List<EntityProcessLink> findEndedActiveForReconciliation(int limit) {
        return findEndedActiveForReconciliationRows(new OffsetPage<>(0, limit));
    }

    /** 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。 */
    @Select("""
            <script>
            SELECT link.* FROM entity_process_link link
            WHERE link.state = 'ACTIVE'
              AND link.process_instance_id IS NOT NULL
              AND EXISTS (
                SELECT 1
                FROM ACT_HI_PROCINST historic
                WHERE historic.PROC_INST_ID_ = link.process_instance_id
                  AND historic.END_TIME_ IS NOT NULL
              )
            ORDER BY link.update_time
            </script>
            """)
    List<EntityProcessLink> findEndedActiveForReconciliationRows(
            @Param("page") IPage<EntityProcessLink> page);
}
