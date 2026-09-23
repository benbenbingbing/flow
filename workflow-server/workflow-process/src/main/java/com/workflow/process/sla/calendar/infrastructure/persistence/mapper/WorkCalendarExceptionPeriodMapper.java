package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarExceptionPeriod;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 定义工作日历异常时段的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface WorkCalendarExceptionPeriodMapper
        extends BaseMapper<WorkCalendarExceptionPeriod> {

    /**
     * 读取例外日期的工作时段，保持配置顺序及起始分钟排序。
     *
     * @param exceptionId 异常ID，后续用于查询异常ID时定位或关联目标
     * @return 工作日历异常时段集合，供调用方遍历或展示
     */
    default List<WorkCalendarExceptionPeriod> findByExceptionId(String exceptionId) {
        return selectList(Wrappers.<WorkCalendarExceptionPeriod>lambdaQuery()
                .eq(WorkCalendarExceptionPeriod::getExceptionId, exceptionId)
                .orderByAsc(WorkCalendarExceptionPeriod::getSortOrder)
                .orderByAsc(WorkCalendarExceptionPeriod::getStartMinute));
    }

    /**
     * 例外时段没有逻辑删除字段，按例外日期记录物理删除。
     *
     * @param exceptionId 异常ID，后续用于删除异常ID时定位或关联目标
     * @return 删除后的异常ID结果，供调用方继续处理
     */
    default int deleteByExceptionId(String exceptionId) {
        return delete(Wrappers.<WorkCalendarExceptionPeriod>lambdaQuery()
                .eq(WorkCalendarExceptionPeriod::getExceptionId, exceptionId));
    }
}
