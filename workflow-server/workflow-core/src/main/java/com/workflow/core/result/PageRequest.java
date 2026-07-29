package com.workflow.core.result;

/**
 * Normalized, bounded page request for APIs that use offset pagination.
 */
public record PageRequest(long pageNumber, int pageSize) {

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

    public long offset() {
        try {
            return Math.multiplyExact(pageNumber - 1L, (long) pageSize);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    public int startIndex(int total) {
        if (total < 0) {
            throw new IllegalArgumentException("记录总数不能为负数");
        }
        return offset() >= total ? total : (int) offset();
    }
}
