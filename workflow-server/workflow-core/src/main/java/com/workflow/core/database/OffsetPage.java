package com.workflow.core.database;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** 将现有 offset/limit 接口接入 MyBatis-Plus 分页插件，SQL 生成和执行仍由框架负责。 */
public final class OffsetPage<T> extends Page<T> {
    private final long rowOffset;

    /**
     * 创建不查询总数的分页参数，供已有独立计数或仅获取一批记录的 Mapper 使用。
     * offset 可以不是 limit 的整数倍；二者均需非负，limit=0 表示不返回记录。
     * 不将偏移换算成页码，避免任意偏移在整数除法中丢失。
     */
    public OffsetPage(long offset, long limit) {
        super(1, limit, false);
        if (offset < 0 || limit < 0) {
            throw new IllegalArgumentException("分页偏移和数量不能为负数");
        }
        this.rowOffset = offset;
    }

    /** 向框架方言提供调用方的精确偏移，其余分页行为复用 Page。 */
    @Override
    public long offset() {
        return rowOffset;
    }
}
