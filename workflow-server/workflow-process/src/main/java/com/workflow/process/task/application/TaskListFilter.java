package com.workflow.process.task.application;

import com.workflow.process.task.api.response.TaskVO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 工作台任务列表的统一筛选规则。
 */
public final class TaskListFilter {

    /**
     * 初始化任务列表过滤，保存构造参数供后续方法使用。
     */
    private TaskListFilter() {
    }

    /**
     * 整理过滤数据，供调用方遍历或继续处理。
     *
     * @param tasks 任务集合，供本方法处理过滤时使用
     * @param keyword 关键字，供本方法处理过滤时使用
     * @param startUserName 启动用户名称，后续用于处理过滤时匹配或展示
     * @param priority 优先级，供本方法处理过滤时使用
     * @param startDate 启动日期，后续用于判断有效期或展示该事件的发生时间
     * @param endDate 结束日期，后续用于判断有效期或展示该事件的发生时间
     * @return 任务集合，供调用方遍历或展示
     */
    public static List<TaskVO> filter(
            List<TaskVO> tasks,
            String keyword,
            String startUserName,
            String priority,
            LocalDate startDate,
            LocalDate endDate) {
        return tasks.stream()
                .filter(task -> matchesKeyword(task, keyword))
                .filter(task -> contains(task.getStartUserName(), startUserName))
                .filter(task -> matchesPriority(task.getPriority(), priority))
                .filter(task -> matchesDate(task.getCreateTime(), startDate, endDate))
                .toList();
    }

    /**
     * 判断是否匹配关键字；判断结果决定调用方的后续分支。
     *
     * @param task 任务，作为 {@code contains} 的输入影响后续处理
     * @param keyword 关键字，供本方法判断是否匹配关键字时使用
     * @return 关键字条件成立时为 true，否则为 false
     */
    private static boolean matchesKeyword(TaskVO task, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        return contains(task.getProcessName(), keyword)
                || contains(task.getTaskName(), keyword)
                || contains(task.getCurrentTaskName(), keyword)
                || contains(task.getName(), keyword)
                || contains(task.getDataName(), keyword)
                || contains(task.getCode(), keyword)
                || contains(task.getBusinessKey(), keyword);
    }

    /**
     * 判断是否匹配优先级；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否匹配优先级的原始输入，结果供调用方继续使用
     * @param priority 优先级，供本方法判断是否匹配优先级时使用
     * @return 优先级条件成立时为 true，否则为 false
     */
    private static boolean matchesPriority(Integer value, String priority) {
        if (isBlank(priority)) {
            return true;
        }
        int normalized = value == null ? 0 : value;
        return switch (priority.trim().toUpperCase(Locale.ROOT)) {
            case "URGENT" -> normalized >= 80;
            case "HIGH" -> normalized >= 50 && normalized < 80;
            case "NORMAL" -> normalized < 50;
            default -> true;
        };
    }

    /**
     * 判断是否匹配日期；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否匹配日期的原始输入，结果供调用方继续使用
     * @param startDate 启动日期，后续用于判断有效期或展示该事件的发生时间
     * @param endDate 结束日期，后续用于判断有效期或展示该事件的发生时间
     * @return 日期条件成立时为 true，否则为 false
     */
    private static boolean matchesDate(Date value, LocalDate startDate, LocalDate endDate) {
        if (value == null) {
            return startDate == null && endDate == null;
        }
        LocalDate taskDate = value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return (startDate == null || !taskDate.isBefore(startDate))
                && (endDate == null || !taskDate.isAfter(endDate));
    }

    /**
     * 判断是否包含任务列表过滤；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否包含任务列表过滤的原始输入，结果供调用方继续使用
     * @param expected 预期，供本方法判断是否包含任务列表过滤时使用
     * @return 任务列表过滤条件成立时为 true，否则为 false
     */
    private static boolean contains(String value, String expected) {
        if (isBlank(expected)) {
            return true;
        }
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 判断是否空白；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空白的原始输入，结果供调用方继续使用
     * @return 空白条件成立时为 true，否则为 false
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
