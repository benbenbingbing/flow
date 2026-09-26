package com.workflow.process.instance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.mybatis.OffsetPage;
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
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.query.DatabaseSort;

import java.util.List;

// 普通外层分页由 MyBatis-Plus 处理；锁定与嵌套分页仍保留必要的数据库适配。
/**
 * 定义实体流程链接的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface EntityProcessLinkMapper extends BaseMapper<EntityProcessLink> {

    // JDBC 方言执行器写入后不能复用 MyBatis 的旧缓存，两种锁定读取都必须访问数据库。
    /**
     * 查询更新；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param generation {@code generation}，供本方法查询更新时使用
     * @return 查询后的更新结果，供调用方继续处理
     */
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

    /**
     * 查询最新更新；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 查询后的最新更新结果，供调用方继续处理
     */
    @SelectProvider(type = LockingSql.class, method = "latest")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    EntityProcessLink selectLatestForUpdate(
            @Param("entityCode") String entityCode,
            @Param("entityRecordId") String entityRecordId);

    /** 调用方已串行化实体写入；缺失代次的排他性最终依靠唯一键占用，不依赖 gap lock。 */
    class LockingSql {
        /**
         * 生成最新文本，供后续匹配或展示。
         *
         * @param context 执行上下文，向后续最新步骤传递身份、配置或状态
         * @return 处理后的最新文本，供调用方比较或展示
         */
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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 查询后的最新结果，供调用方继续处理
     */
    default EntityProcessLink selectLatest(String entityCode, String entityRecordId) {
        return selectList(new Page<EntityProcessLink>(1, 1, false), Wrappers.<EntityProcessLink>lambdaQuery()
                .eq(EntityProcessLink::getEntityCode, entityCode)
                .eq(EntityProcessLink::getEntityRecordId, entityRecordId)
                .orderByDesc(EntityProcessLink::getGeneration)).stream().findFirst().orElse(null);
    }

    /**
     * 激活实体流程链接；结果供调用方的后续步骤使用。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param requestId 请求ID，后续用于激活实体流程链接时定位或关联目标
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 激活后的实体流程链接结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE entity_process_link
            SET process_instance_id = #{processInstanceId},
                state = 'ACTIVE',
                version = version + 1,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id}
              AND request_id = #{requestId}
              AND state = 'PENDING'
            </script>
            """)
    int activate(
            @Param("id") String id,
            @Param("requestId") String requestId,
            @Param("processInstanceId") String processInstanceId);

    /**
     * 处理关闭活动，并将结果传给后续步骤。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityStatus 实体状态标识，决定后续关闭活动采用的处理分支
     * @return 处理后的关闭活动结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE entity_process_link
            SET state = 'ENDED',
                entity_status = COALESCE(#{entityStatus,jdbcType=VARCHAR}, entity_status),
                ended_at = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                version = version + 1,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE process_instance_id = #{processInstanceId}
              AND state = 'ACTIVE'
            </script>
            """)
    int closeActive(
            @Param("processInstanceId") String processInstanceId,
            @Param("entityStatus") String entityStatus);

    /**
     * 在关联关闭事务中独立记录真实结束原因，生命周期仍统一为 ENDED。
     *
     * @param instanceId 实例ID，后续用于记录结束类型时定位或关联目标
     * @param endType 结束类型标识，决定后续结束类型采用的处理分支
     * @return 记录后的结束类型结果，供调用方继续处理
     */
    default int recordEndType(String instanceId, String endType) {
        return update(null, Wrappers.<EntityProcessLink>lambdaUpdate()
                .set(EntityProcessLink::getEndType, endType)
                .eq(EntityProcessLink::getProcessInstanceId, instanceId));
    }

    /**
     * 更新活动状态；后续读取或执行将使用更新后的状态。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityStatus 实体状态标识，决定后续活动状态采用的处理分支
     * @return 更新后的活动状态结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE entity_process_link
            SET entity_status = #{entityStatus,jdbcType=VARCHAR},
                version = version + 1,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE process_instance_id = #{processInstanceId}
              AND state = 'ACTIVE'
            </script>
            """)
    int updateActiveStatus(
            @Param("processInstanceId") String processInstanceId,
            @Param("entityStatus") String entityStatus);

    /**
     * 按流程实例读取关联记录，首行限制由框架分页方言处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的实体流程链接结果，供调用方继续处理
     */
    default EntityProcessLink findByProcessInstanceId(String processInstanceId) {
        return selectList(new Page<EntityProcessLink>(1, 1, false), Wrappers.<EntityProcessLink>lambdaQuery()
                .eq(EntityProcessLink::getProcessInstanceId, processInstanceId)).stream().findFirst().orElse(null);
    }

    /**
     * 查询结束活动{@code reconciliation}；查询结果供调用方展示或继续处理。
     *
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 实体流程链接集合，供调用方遍历或展示
     */
    default List<EntityProcessLink> findEndedActiveForReconciliation(int limit) {
        return findEndedActiveForReconciliationRows(new OffsetPage<>(0, limit));
    }

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @return 实体流程链接集合，供调用方遍历或展示
     */
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
