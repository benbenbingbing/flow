package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarExceptionPeriod;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkCalendarExceptionPeriodMapper
        extends BaseMapper<WorkCalendarExceptionPeriod> {

    @Select("""
            SELECT * FROM work_calendar_exception_period
            WHERE exception_id = #{exceptionId}
            ORDER BY sort_order, start_minute
            """)
    List<WorkCalendarExceptionPeriod> findByExceptionId(
            @Param("exceptionId") String exceptionId);

    @Delete("""
            DELETE FROM work_calendar_exception_period
            WHERE exception_id = #{exceptionId}
            """)
    int deleteByExceptionId(@Param("exceptionId") String exceptionId);
}
