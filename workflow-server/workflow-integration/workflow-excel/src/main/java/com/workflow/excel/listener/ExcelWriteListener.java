package com.workflow.excel.listener;

import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.handler.context.RowWriteHandlerContext;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 通用写监听器，统计完成写入的数据行并提供行完成回调，供进度展示或调整行样式使用。
 * 一次导出创建一个实例，不可跨文件或并发导出共享；支持普通 write，模板 fill 可能重复触发行回调。
 * 不执行 DTO 字段校验；需要导出校验时应在写入前显式使用 ExcelValidator。
 */
public final class ExcelWriteListener implements RowWriteHandler {
    private final Consumer<RowWriteHandlerContext> rowConsumer;
    private long rowCount;

    /** 创建只统计数据行数的监听器，表头行不计入。 */
    public ExcelWriteListener() {
        this(context -> { });
    }

    /**
     * 创建支持每行完成回调的监听器。
     *
     * @param rowConsumer 每个数据行完成后的同步回调，context 提供 Sheet、原始行号及 POI Row；
     *                    回调异常会中止导出，已经写入的内容应由调用方丢弃
     */
    public ExcelWriteListener(Consumer<RowWriteHandlerContext> rowConsumer) {
        this.rowConsumer = Objects.requireNonNull(rowConsumer, "写入回调不能为空");
    }

    /** 表头不触发业务回调；行完成仅代表内存中的行已构建，整个文件成功仍以 writer 关闭为准。 */
    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        if (!Boolean.TRUE.equals(context.getHead())) {
            rowCount++;
            rowConsumer.accept(context);
        }
    }

    /** @return 已完成的数据行回调次数，普通 write 中即为数据行数，不包含表头 */
    public long getRowCount() {
        return rowCount;
    }
}
