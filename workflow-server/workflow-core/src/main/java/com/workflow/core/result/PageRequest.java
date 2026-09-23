package com.workflow.core.result;

/**
 * Normalized, bounded page request for APIs that use offset pagination.
 *
 * @param pageNumber 从 1 开始的页码，用于计算数据库查询偏移
 * @param pageSize 单页记录数，用于限制查询和计算偏移
 */
public record PageRequest(long pageNumber, int pageSize) {

    /**
     * 将外部页码和页大小限制到有效范围，供后续偏移分页使用。
     *
     * @param requestedPageNumber 调用方请求的页码，后续规范为不小于 1 的值
     * @param requestedPageSize 调用方请求的页大小，后续按默认值和最大值约束
     * @param defaultPageSize 未指定有效页大小时采用的默认记录数
     * @param maximumPageSize 单页允许的最大记录数，防止一次查询过多数据
     * @return 页码从 1 开始、页大小不超过上限的分页请求
     * @throws IllegalArgumentException 默认页大小无效，或最大页大小小于默认值时抛出
     */
    public static PageRequest normalize(
            Integer requestedPageNumber,
            Integer requestedPageSize,
            int defaultPageSize,
            int maximumPageSize) {
        if (defaultPageSize < 1 || maximumPageSize < defaultPageSize) {
            throw new IllegalArgumentException("分页配置不合法");
        }
        long pageNumber = requestedPageNumber == null
                ? 1L
                : Math.max(1L, requestedPageNumber.longValue());
        int pageSize = requestedPageSize == null
                ? defaultPageSize
                : Math.min(maximumPageSize, Math.max(1, requestedPageSize));
        return new PageRequest(pageNumber, pageSize);
    }

    /**
     * 计算当前页在数据库查询中的零基偏移；溢出时返回可表示的最大值。
     *
     * @return 当前页的零基查询偏移；乘法溢出时返回 {@link Long#MAX_VALUE}
     */
    public long offset() {
        try {
            return Math.multiplyExact(pageNumber - 1L, (long) pageSize);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 根据总记录数计算当前页起始索引，供内存分页使用。
     *
     * @param total 记录总数，用于将起始索引限制在有效范围内
     * @return 当前页在结果列表中的起始索引，超出总数时取 {@code total}
     * @throws IllegalArgumentException {@code total} 为负数时抛出
     */
    public int startIndex(int total) {
        if (total < 0) {
            throw new IllegalArgumentException("记录总数不能为负数");
        }
        return offset() >= total ? total : (int) offset();
    }
}
