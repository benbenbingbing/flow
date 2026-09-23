package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarException;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WorkCalendarExceptionMapper extends BaseMapper<WorkCalendarException> {

    /** 读取日历的例外日期，按日期顺序返回。 */
    default List<WorkCalendarException> findByCalendarId(String calendarId) {
        return selectList(Wrappers.<WorkCalendarException>lambdaQuery()
                .eq(WorkCalendarException::getCalendarId, calendarId)
                .orderByAsc(WorkCalendarException::getExceptionDate));
    }

    default WorkCalendarException findByDate(String calendarId, LocalDate date) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<WorkCalendarException>(1, 1, false), Wrappers.<WorkCalendarException>lambdaQuery()
                .eq(WorkCalendarException::getCalendarId, calendarId)
                .eq(WorkCalendarException::getExceptionDate, date)).stream().findFirst().orElse(null);
    }

    /** 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。 */
    default int deleteByCalendarId(String calendarId) {
        return delete(Wrappers.<WorkCalendarException>lambdaQuery()
                .eq(WorkCalendarException::getCalendarId, calendarId));
    }
}
