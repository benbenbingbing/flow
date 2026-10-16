package com.workflow.contracts.process.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 公共分页参数；不暴露数据库或 HTTP 分页类型。
 * @param pageNumber 从 1 开始的页码
 * @param pageSize 每页 1 至 100 条
 * @param sort 排序方向，空值使用时间降序
 */
public record ProcessPageRequest(
        int pageNumber,
        int pageSize,
        ProcessSort sort) {
    public ProcessPageRequest {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数超出范围");
        if ((long) (pageNumber - 1) * pageSize > Integer.MAX_VALUE) throw new IllegalArgumentException("分页偏移超出范围");
        sort = sort == null ? ProcessSort.NEWEST_FIRST : sort;
    }
    /** 返回公共接口默认的第一页，每页 20 条。 */
    public static ProcessPageRequest defaults() { return new ProcessPageRequest(1, 20, ProcessSort.NEWEST_FIRST); }
    /** 返回已经过溢出检查的查询偏移。 */
    public int offset() { return (pageNumber - 1) * pageSize; }

}
