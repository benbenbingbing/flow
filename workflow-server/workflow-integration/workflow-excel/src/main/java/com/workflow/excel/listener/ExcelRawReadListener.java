package com.workflow.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.workflow.excel.converter.DateWorkbookSupport;
import com.workflow.excel.dynamic.ExcelRawCell;
import com.workflow.excel.dynamic.ExcelRawRow;
import com.workflow.excel.dynamic.ExcelRawMetadata;
import com.workflow.excel.ExcelReadOptions;
import com.alibaba.excel.read.metadata.holder.ReadWorkbookHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** 原始读取监听器仅缓冲一批，不执行业务校验；公式及错误单元格交给业务收集多个错误。 */
public final class ExcelRawReadListener extends AnalysisEventListener<Map<Integer, ReadCellData<?>>> {
    private final int batchSize;
    private final long maxRows;
    private final Consumer<ExcelRawRow> headers;
    private final Consumer<List<ExcelRawRow>> batches;
    private final List<ExcelRawRow> buffer = new ArrayList<>();
    private long rowCount;
    private ExcelRawMetadata metadata;
    private int headRowNumber;

    /** maxRows 限制回调的数据行（含中间空行），表头不计入；避免导入错误文件无限积累。 */
    public ExcelRawReadListener(int batchSize, long maxRows, Consumer<ExcelRawRow> headers,
            Consumer<List<ExcelRawRow>> batches) {
        if (batchSize <= 0 || maxRows <= 0) throw new IllegalArgumentException("批大小和行数上限必须为正数");
        this.batchSize = batchSize;
        this.maxRows = maxRows;
        this.headers = Objects.requireNonNull(headers, "表头回调不能为空");
        this.batches = Objects.requireNonNull(batches, "数据批次回调不能为空");
    }

    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        drainBefore(context.readRowHolder().getRowIndex(), context);
        headers.accept(snapshot(headMap, context));
    }

    @Override
    public void invoke(Map<Integer, ReadCellData<?>> data, AnalysisContext context) {
        drainBefore(context.readRowHolder().getRowIndex(), context);
        acceptData(snapshot(data, context));
    }

    private void acceptData(ExcelRawRow row) {
        if (++rowCount > maxRows) throw new IllegalArgumentException("Excel 数据行数超过上限：" + maxRows);
        buffer.add(row);
        if (buffer.size() >= batchSize) flush();
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        drainBefore(Integer.MAX_VALUE, context);
        flush();
    }

    /** 必须在读取 Sheet 数据前初始化，用于补齐解析器漏掉的空公式及错误类型。 */
    public void initialize(ReadWorkbookHolder holder, ExcelReadOptions options) {
        headRowNumber = options.headRowNumber();
        metadata = ExcelRawMetadata.read(holder, options, maxRows);
    }

    public long getRowCount() { return rowCount; }

    private ExcelRawRow snapshot(Map<Integer, ReadCellData<?>> data, AnalysisContext context) {
        return snapshot(data, context, context.readRowHolder().getRowIndex());
    }

    private ExcelRawRow snapshot(Map<Integer, ReadCellData<?>> data, AnalysisContext context, int rowIndex) {
        boolean use1904 = DateWorkbookSupport.use1904(context.readWorkbookHolder());
        Map<Integer, ExcelRawCell> cells = new LinkedHashMap<>();
        data.forEach((column, cell) -> cells.put(column, ExcelRawCell.from(cell, column, use1904)));
        if (metadata != null) metadata.take(rowIndex).forEach((column, override) -> {
            ExcelRawCell original = cells.get(column);
            cells.put(column, new ExcelRawCell(column, override.type(), override.value(),
                    original == null ? override.formatIndex() : original.formatIndex(),
                    original == null ? override.format() : original.format(), use1904));
        });
        return new ExcelRawRow(context.readSheetHolder().getSheetNo(), context.readSheetHolder().getSheetName(),
                rowIndex, use1904, cells);
    }

    private void drainBefore(int rowIndex, AnalysisContext context) {
        // XLS 的一整行空字符串公式可能完全不触发回调，按原始行号补齐且不重复普通行。
        while (metadata != null && metadata.firstRow() != null && metadata.firstRow() < rowIndex) {
            int missing = metadata.firstRow();
            ExcelRawRow row = snapshot(Map.of(), context, missing);
            if (missing < headRowNumber) headers.accept(row);
            else acceptData(row);
        }
    }

    private void flush() {
        if (buffer.isEmpty()) return;
        // 调用方可暂存当前批次；下一批不能改写其集合内容。
        List<ExcelRawRow> batch = List.copyOf(buffer);
        buffer.clear();
        batches.accept(batch);
    }
}
