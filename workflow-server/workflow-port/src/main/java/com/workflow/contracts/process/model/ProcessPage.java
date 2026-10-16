package com.workflow.contracts.process.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 授权与筛选后的分页结果；total 必须与 items 使用相同查询范围。
 * @param items 当前页的独立投影
 * @param total 符合查询条件的总条数
 * @param pageNumber 从 1 开始的页码
 * @param pageSize 请求的分页大小
 */
public record ProcessPage<T>(
        List<T> items,
        long total,
        int pageNumber,
        int pageSize) {
    public ProcessPage {
        items = List.copyOf(items);

    }

}
