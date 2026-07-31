package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WorkCalendarBindingMapper extends BaseMapper<WorkCalendarBinding> {

    @Select("""
            SELECT * FROM work_calendar_binding
            WHERE calendar_id = #{calendarId}
              AND deleted = 0
            ORDER BY priority DESC, create_time
            """)
    List<WorkCalendarBinding> findByCalendarId(
            @Param("calendarId") String calendarId);

    @Delete("DELETE FROM work_calendar_binding WHERE calendar_id = #{calendarId}")
    int deleteByCalendarId(@Param("calendarId") String calendarId);

    @Select("""
            SELECT * FROM work_calendar_binding
            WHERE status = 'ENABLED'
              AND deleted = 0
            ORDER BY scope_type, scope_key, priority DESC, update_time DESC
            """)
    List<WorkCalendarBinding> findAllEnabled();

    @Select("""
            SELECT * FROM work_calendar_binding
            WHERE scope_type = #{scopeType}
              AND scope_key = #{scopeKey}
              AND status = 'ENABLED'
              AND deleted = 0
              AND (effective_from IS NULL OR effective_from <= #{date})
              AND (effective_to IS NULL OR effective_to >= #{date})
            ORDER BY priority DESC, update_time DESC
            """)
    List<WorkCalendarBinding> findEffective(
            @Param("scopeType") String scopeType,
            @Param("scopeKey") String scopeKey,
            @Param("date") LocalDate date);
}
