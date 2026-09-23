package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarException;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 定义工作日历异常的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface WorkCalendarExceptionMapper extends BaseMapper<WorkCalendarException> {

    /**
     * 读取日历的例外日期，按日期顺序返回。
     *
     * @param calendarId 日历ID，后续用于查询日历ID时定位或关联目标
     * @return 工作日历异常集合，供调用方遍历或展示
     */
    default List<WorkCalendarException> findByCalendarId(String calendarId) {
        return selectList(Wrappers.<WorkCalendarException>lambdaQuery()
                .eq(WorkCalendarException::getCalendarId, calendarId)
                .orderByAsc(WorkCalendarException::getExceptionDate));
    }

    /**
     * 按日期查询工作日历异常；结果供后续展示或处理。
     *
     * @param calendarId 日历ID，后续用于查询日期时定位或关联目标
     * @param date 日期，后续用于判断有效期或展示该事件的发生时间
     * @return 符合条件的工作日历异常结果，供调用方继续处理
     */
    default WorkCalendarException findByDate(String calendarId, LocalDate date) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<WorkCalendarException>(1, 1, false), Wrappers.<WorkCalendarException>lambdaQuery()
                .eq(WorkCalendarException::getCalendarId, calendarId)
                .eq(WorkCalendarException::getExceptionDate, date)).stream().findFirst().orElse(null);
    }

    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param calendarId 日历ID，后续用于删除日历ID时定位或关联目标
     * @return 删除后的日历ID结果，供调用方继续处理
     */
    default int deleteByCalendarId(String calendarId) {
        return delete(Wrappers.<WorkCalendarException>lambdaQuery()
                .eq(WorkCalendarException::getCalendarId, calendarId));
    }
}
