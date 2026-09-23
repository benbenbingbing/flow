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

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化实体列表分页结果{@code normalizer}的原始输入，结果供调用方继续使用
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @return 规范化后的实体列表分页结果{@code normalizer}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 整理首个列表数据，供调用方遍历或继续处理。
     *
     * @param candidates 候选集合，供本方法处理首个列表时使用
     * @return {@code list<?>}集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理首个，并将结果传给后续步骤。
     *
     * @param candidates 候选集合，供本方法处理首个时使用
     * @return 处理后的首个结果，供调用方继续处理
     */
    private Object first(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 处理非{@code negative}{@code long}，并将结果传给后续步骤。
     *
     * @param value 待处理非{@code negative}{@code long}的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @param field 字段，作为 {@code number} 的输入影响后续处理
     * @return 处理后的非{@code negative}{@code long}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理正数{@code long}，并将结果传给后续步骤。
     *
     * @param value 待处理正数{@code long}的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @param field 字段，作为 {@code number} 的输入影响后续处理
     * @return 处理后的正数{@code long}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理数值，并将结果传给后续步骤。
     *
     * @param value 待处理数值的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @param field 字段，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的数值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理正数，并将结果传给后续步骤。
     *
     * @param value 待处理正数的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的正数结果，供调用方继续处理
     */
    private long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }
}
