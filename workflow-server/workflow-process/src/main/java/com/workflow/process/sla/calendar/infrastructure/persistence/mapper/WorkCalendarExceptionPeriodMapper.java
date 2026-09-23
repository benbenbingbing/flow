package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarExceptionPeriod;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkCalendarExceptionPeriodMapper
        extends BaseMapper<WorkCalendarExceptionPeriod> {

    /** 读取例外日期的工作时段，保持配置顺序及起始分钟排序。 */
    default List<WorkCalendarExceptionPeriod> findByExceptionId(String exceptionId) {
        return selectList(Wrappers.<WorkCalendarExceptionPeriod>lambdaQuery()
                .eq(WorkCalendarExceptionPeriod::getExceptionId, exceptionId)
                .orderByAsc(WorkCalendarExceptionPeriod::getSortOrder)
                .orderByAsc(WorkCalendarExceptionPeriod::getStartMinute));
    }

    /** 例外时段没有逻辑删除字段，按例外日期记录物理删除。 */
    default int deleteByExceptionId(String exceptionId) {
        return delete(Wrappers.<WorkCalendarExceptionPeriod>lambdaQuery()
                .eq(WorkCalendarExceptionPeriod::getExceptionId, exceptionId));
    }
}
