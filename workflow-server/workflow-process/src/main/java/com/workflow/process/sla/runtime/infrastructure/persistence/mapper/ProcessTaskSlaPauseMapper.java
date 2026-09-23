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
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseSort;

import java.util.List;

@Mapper
public interface ProcessTaskSlaPauseMapper
        extends BaseMapper<ProcessTaskSlaPause> {

    /** 读取任务 SLA 的暂停区间，按暂停开始时间排列。 */
    default List<ProcessTaskSlaPause> findBySlaId(String slaId) {
        return selectList(Wrappers.<ProcessTaskSlaPause>lambdaQuery()
                .eq(ProcessTaskSlaPause::getSlaId, slaId)
                .orderByAsc(ProcessTaskSlaPause::getStartedAt));
    }

    /** 调用方先持有 SLA 主行锁；同一开始时间以主键打破平局。锁定读取不复用 MyBatis 缓存。 */
    @SelectProvider(type = LockingSql.class, method = "open")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ProcessTaskSlaPause findOpenForUpdate(@Param("slaId") String slaId);

    /** SLA 条件由业务模块定义，只把首行锁语法交给 integration。 */
    class LockingSql {
        public static String open(ProviderContext context) {
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).firstForUpdate(
                    "process_task_sla_pause", "sla_id = #{slaId} AND resumed_at IS NULL",
                    List.of(new DatabaseSort("started_at", true), new DatabaseSort("id", true)), "id");
        }
    }
}
