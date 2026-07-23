package com.workflow.common;

import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
public class PageResult<T> {
    
    /**
     * 数据列表
     */
    private List<T> records;
    
    /**
     * 总记录数
     */
    private long total;
    
    /**
     * 当前页
     */
    private long pageNum;
    
    /**
     * 每页大小
     */
    private long pageSize;
    
    public PageResult() {
    }

    /**
     * 全参构造方法。
     *
     * @param records  当前页的数据列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     */
    public PageResult(List<T> records, long total, long pageNum, long pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
}
