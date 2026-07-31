package com.workflow.process.sla.runtime.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaPause;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcessTaskSlaPauseMapper
        extends BaseMapper<ProcessTaskSlaPause> {

    @Select("""
            SELECT * FROM process_task_sla_pause
            WHERE sla_id = #{slaId}
            ORDER BY started_at
            """)
    List<ProcessTaskSlaPause> findBySlaId(@Param("slaId") String slaId);

    @Select("""
            SELECT * FROM process_task_sla_pause
            WHERE sla_id = #{slaId}
              AND resumed_at IS NULL
            ORDER BY started_at DESC
            LIMIT 1 FOR UPDATE
            """)
    ProcessTaskSlaPause findOpenForUpdate(@Param("slaId") String slaId);
}
