package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign;
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

/**
 * 任务加签操作 Mapper
 * 提供加签操作的查询、加锁、计数、取消操作
 */
@Mapper
public interface ProcessTaskAddSignMapper extends BaseMapper<ProcessTaskAddSign> {
    /**
     * 根据源任务ID查询进行中的加签记录（ACTIVE/WAITING_SOURCE）。
     *
     * @param taskId 源任务ID
     * @return 最近一条进行中的加签记录，无则返回 null
     */
    default ProcessTaskAddSign findOpenBySourceTaskId(String taskId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessTaskAddSign>(1, 1, false), Wrappers.<ProcessTaskAddSign>lambdaQuery()
                .eq(ProcessTaskAddSign::getSourceTaskId, taskId)
                .in(ProcessTaskAddSign::getStatus, "ACTIVE", "WAITING_SOURCE")
                .orderByDesc(ProcessTaskAddSign::getCreateTime)).stream().findFirst().orElse(null);
    }

    /**
     * 根据源任务ID加锁查询进行中的加签记录（FOR UPDATE），每次访问数据库而不复用缓存。
     *
     * @param taskId 源任务ID
     * @return 最近一条进行中的加签记录，无则返回 null
     */
    @SelectProvider(type = LockingSql.class, method = "open")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ProcessTaskAddSign findOpenBySourceTaskIdForUpdate(@Param("taskId") String taskId);

    /** 同一创建时间按主键稳定取一条，状态过滤仍属于加签业务。 */
    class LockingSql {
        public static String open(ProviderContext context) {
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).firstForUpdate(
                    "process_task_add_sign", "source_task_id = #{taskId} AND status IN ('ACTIVE','WAITING_SOURCE')",
                    List.of(new DatabaseSort("create_time", true), new DatabaseSort("id", true)), "id");
        }
    }

    /**
     * 根据加签ID加锁查询加签记录（FOR UPDATE）。
     *
     * @param addSignId 加签记录ID
     * @return 加签记录，无则返回 null
     */
    // 主键查询至多一行，直接保留 FOR UPDATE 即可锁定原目标。
    @Select("SELECT * FROM process_task_add_sign WHERE id = #{addSignId} FOR UPDATE")
    ProcessTaskAddSign selectByIdForUpdate(@Param("addSignId") String addSignId);

    /**
     * 统计源任务下进行中（ACTIVE/WAITING_SOURCE）的加签数。
     * <p>
     * 用于判断源任务是否被加签阻塞，暂不能完成。
     *
     * @param taskId 源任务ID
     * @return 阻塞中的加签数
     */
    default long countBlockingBySourceTaskId(String taskId) {
        return selectCount(Wrappers.<ProcessTaskAddSign>lambdaQuery()
                .eq(ProcessTaskAddSign::getSourceTaskId, taskId)
                .in(ProcessTaskAddSign::getStatus, "ACTIVE", "WAITING_SOURCE"));
    }

    /**
     * 取消进行中的加签操作（置为 CANCELLED）。
     *
     * @param addSignId 加签记录ID
     * @return 受影响行数
     */
    @Update("""
            <script>
            UPDATE process_task_add_sign SET status = 'CANCELLED', complete_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE id = #{addSignId}
             AND status IN ('ACTIVE','WAITING_SOURCE')
            </script>
            """)
    int cancel(@Param("addSignId") String addSignId);
}
