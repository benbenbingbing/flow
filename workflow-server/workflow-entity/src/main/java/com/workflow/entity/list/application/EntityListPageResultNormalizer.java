package com.workflow.entity.list.application;

import com.workflow.core.result.PageResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Normalizes custom list query results to the platform page contract.
 */
@Component
public class EntityListPageResultNormalizer {

    public PageResult<?> normalize(
            Object value,
            long requestedPageNum,
            long requestedPageSize) {
        long pageNum = Math.max(1, requestedPageNum);
        int pageSize = (int) Math.max(
                1,
                Math.min(200, requestedPageSize));
        if (value == null) {
            return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }
        if (value instanceof PageResult<?> page) {
            return new PageResult<>(
                    page.getRecords() == null
                            ? List.of() : page.getRecords(),
                    Math.max(0, page.getTotal()),
                    positive(page.getPageNum(), pageNum),
                    positive(page.getPageSize(), pageSize));
        }
        if (value instanceof List<?> rows) {
            int start = (int) Math.min(
                    Math.max(0, (pageNum - 1) * pageSize),
                    rows.size());
            int end = Math.min(start + pageSize, rows.size());
            return new PageResult<>(
                    rows.subList(start, end),
                    rows.size(),
                    pageNum,
                    pageSize);
        }
        if (!(value instanceof Map<?, ?> result)) {
            throw new IllegalArgumentException(
                    "列表查询结果必须为分页对象或数组");
        }
        List<?> records = firstList(
                result.get("records"),
                result.get("list"),
                result.get("rows"));
        if (records == null) {
            throw new IllegalArgumentException(
                    "列表查询结果缺少 records、list 或 rows 数组");
        }
        long total = nonNegativeLong(
                result.get("total"),
                records.size(),
                "total");
        long actualPageNum = positiveLong(
                first(result.get("pageNum"), result.get("current")),
                pageNum,
                "pageNum/current");
        long actualPageSize = positiveLong(
                first(result.get("pageSize"), result.get("size")),
                pageSize,
                "pageSize/size");
        return new PageResult<>(
                records,
                total,
                actualPageNum,
                actualPageSize);
    }

    private List<?> firstList(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof List<?> rows) {
                return rows;
            }
            if (candidate != null) {
                throw new IllegalArgumentException(
                        "列表查询结果的数据列表字段必须为数组");
            }
        }
        return null;
    }

    private Object first(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private long nonNegativeLong(
            Object value,
            long fallback,
            String field) {
        long parsed = number(value, fallback, field);
        if (parsed < 0) {
            throw new IllegalArgumentException(
                    "列表查询结果 " + field + " 不能小于 0");
        }
        return parsed;
    }

    private long positiveLong(
            Object value,
            long fallback,
            String field) {
        long parsed = number(value, fallback, field);
        if (parsed < 1) {
            throw new IllegalArgumentException(
                    "列表查询结果 " + field + " 必须大于 0");
        }
        return parsed;
    }

    private long number(
            Object value,
            long fallback,
            String field) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "列表查询结果 " + field + " 必须为数字");
        }
    }

    private long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }
}
