package com.workflow.process.sla.runtime.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaPause;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.query.DatabaseSort;

import java.util.List;

/**
 * 定义流程任务SLA{@code pause}的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface ProcessTaskSlaPauseMapper
        extends BaseMapper<ProcessTaskSlaPause> {

    /**
     * 读取任务 SLA 的暂停区间，按暂停开始时间排列。
     *
     * @param slaId SLAID，后续用于查询SLAID时定位或关联目标
     * @return 流程任务SLA{@code pause}集合，供调用方遍历或展示
     */
    default List<ProcessTaskSlaPause> findBySlaId(String slaId) {
        return selectList(Wrappers.<ProcessTaskSlaPause>lambdaQuery()
                .eq(ProcessTaskSlaPause::getSlaId, slaId)
                .orderByAsc(ProcessTaskSlaPause::getStartedAt));
    }

    /**
     * 调用方先持有 SLA 主行锁；同一开始时间以主键打破平局。锁定读取不复用 MyBatis 缓存。
     *
     * @param slaId SLAID，后续用于查询打开更新时定位或关联目标
     * @return 符合条件的流程任务SLA{@code pause}结果，供调用方继续处理
     */
    @SelectProvider(type = LockingSql.class, method = "open")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ProcessTaskSlaPause findOpenForUpdate(@Param("slaId") String slaId);

    /** SLA 条件由业务模块定义，只把首行锁语法交给 integration。 */
    class LockingSql {
        /**
         * 生成打开文本，供后续匹配或展示。
         *
         * @param context 执行上下文，向后续打开步骤传递身份、配置或状态
         * @return 处理后的打开文本，供调用方比较或展示
         */
        public static String open(ProviderContext context) {
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).firstForUpdate(
                    "process_task_sla_pause", "sla_id = #{slaId} AND resumed_at IS NULL",
                    List.of(new DatabaseSort("started_at", true), new DatabaseSort("id", true)), "id");
        }
    }
}
