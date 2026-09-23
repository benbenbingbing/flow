package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkCalendarMapper extends BaseMapper<WorkCalendar> {

    default WorkCalendar findByCode(String calendarCode) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<WorkCalendar>(1, 1, false), Wrappers.<WorkCalendar>lambdaQuery()
                .eq(WorkCalendar::getCalendarCode, calendarCode)
                .orderByDesc(WorkCalendar::getVersion)).stream().findFirst().orElse(null);
    }

    /** 按业务编码读取最大版本；聚合使用标准 SQL，过滤及逻辑删除交给 Wrapper。 */
    default int findMaxVersion(String calendarCode) {
        List<Object> values = selectObjs(Wrappers.<WorkCalendar>query()
                .select("COALESCE(MAX(version), 0)").eq("calendar_code", calendarCode));
        return values.isEmpty() || values.get(0) == null ? 0 : ((Number) values.get(0)).intValue();
    }

    default WorkCalendar findLatestPublished(String calendarCode) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<WorkCalendar>(1, 1, false), Wrappers.<WorkCalendar>lambdaQuery()
                .eq(WorkCalendar::getCalendarCode, calendarCode)
                .eq(WorkCalendar::getStatus, "PUBLISHED")
                .orderByDesc(WorkCalendar::getVersion)).stream().findFirst().orElse(null);
    }

    /** 读取已发布的默认日历，最新版本优先。 */
    default List<WorkCalendar> findPublishedDefaults() {
        return selectList(Wrappers.<WorkCalendar>lambdaQuery()
                .eq(WorkCalendar::getDefaultFlag, 1)
                .eq(WorkCalendar::getStatus, "PUBLISHED")
                .orderByDesc(WorkCalendar::getVersion));
    }

    /** 读取全部已发布的有效日历，默认日历优先并保持名称、版本排序。 */
    default List<WorkCalendar> findPublished() {
        return selectList(Wrappers.<WorkCalendar>lambdaQuery()
                .eq(WorkCalendar::getStatus, "PUBLISHED")
                .orderByDesc(WorkCalendar::getDefaultFlag)
                .orderByAsc(WorkCalendar::getCalendarName)
                .orderByDesc(WorkCalendar::getVersion));
    }
}
