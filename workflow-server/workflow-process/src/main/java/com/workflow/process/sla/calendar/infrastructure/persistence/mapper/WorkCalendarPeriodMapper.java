package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarPeriod;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 定义工作日历时段的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface WorkCalendarPeriodMapper extends BaseMapper<WorkCalendarPeriod> {

    /**
     * 读取日历的每周工作时段，按星期及配置顺序排列。
     *
     * @param calendarId 日历ID，后续用于查询日历ID时定位或关联目标
     * @return 工作日历时段集合，供调用方遍历或展示
     */
    default List<WorkCalendarPeriod> findByCalendarId(String calendarId) {
        return selectList(Wrappers.<WorkCalendarPeriod>lambdaQuery()
                .eq(WorkCalendarPeriod::getCalendarId, calendarId)
                .orderByAsc(WorkCalendarPeriod::getDayOfWeek)
                .orderByAsc(WorkCalendarPeriod::getSortOrder)
                .orderByAsc(WorkCalendarPeriod::getStartMinute));
    }

    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param calendarId 日历ID，后续用于删除日历ID时定位或关联目标
     * @return 删除后的日历ID结果，供调用方继续处理
     */
    default int deleteByCalendarId(String calendarId) {
        return delete(Wrappers.<WorkCalendarPeriod>lambdaQuery()
                .eq(WorkCalendarPeriod::getCalendarId, calendarId));
    }
}
