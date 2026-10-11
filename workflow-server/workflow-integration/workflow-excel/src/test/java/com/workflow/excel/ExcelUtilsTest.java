package com.workflow.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.converters.longconverter.LongStringConverter;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.excel.support.ExcelTypeEnum;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExcelUtilsTest {
    @Test
    void roundTripsAnnotatedRowsAndLeavesCallerStreamsOpen() {
        TrackingOutputStream output = new TrackingOutputStream();
        ExcelUtils.write(output, TestRow.class, List.of(
                new TestRow(9007199254740993L, "张三", new BigDecimal("12.50"), LocalDate.of(2026, 10, 8))), "人员");
        assertFalse(output.closed);

        TrackingInputStream input = new TrackingInputStream(output.toByteArray());
        List<TestRow> rows = ExcelUtils.read(input, TestRow.class);

        assertFalse(input.closed);
        assertEquals(1, rows.size());
        assertEquals(9007199254740993L, rows.get(0).getId());
        assertEquals("张三", rows.get(0).getName());
        assertEquals(0, new BigDecimal("12.50").compareTo(rows.get(0).getAmount()));
        assertEquals(LocalDate.of(2026, 10, 8), rows.get(0).getDay());
        assertEquals(4, ExcelUtils.readRows(input(output.toByteArray())).get(0).size());
    }

    @Test
    void roundTripsDynamicColumnsAndSkipsBothHeaderRows() {
        TrackingOutputStream output = new TrackingOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("人员", "姓名"), List.of("人员", "编号")),
                List.of(List.of("李四", "001"), List.of("=1+1", "002")), "动态数据");

        List<Map<Integer, String>> rows = ExcelUtils.readRows(input(output.toByteArray()),
                new ExcelReadOptions(0, 2, 1));

        assertFalse(output.closed);
        assertEquals(List.of(Map.of(0, "李四", 1, "001"), Map.of(0, "=1+1", 1, "002")), rows);
    }

    @Test
    void readsSelectedSheetWithoutDroppingFirstRowInAHeaderlessFile() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(output).autoCloseStream(false).build()) {
            writer.write(List.of(List.of("忽略")), EasyExcel.writerSheet(0, "第一页").build());
            writer.write(List.of(List.of("第一行"), List.of("第二行")), EasyExcel.writerSheet(1, "第二页").build());
        }

        List<Map<Integer, String>> rows = ExcelUtils.readRows(input(output.toByteArray()),
                new ExcelReadOptions(1, 0, 2));

        assertEquals(List.of(Map.of(0, "第一行"), Map.of(0, "第二行")), rows);
    }

    @Test
    void rejectsMissingSheetInsteadOfReportingSuccessfulEmptyImport() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.write(output, TestRow.class, testRows(1));
        TrackingInputStream input = new TrackingInputStream(output.toByteArray());

        assertThrows(IllegalArgumentException.class,
                () -> ExcelUtils.read(input, TestRow.class, new ExcelReadOptions(1, 1, 500)));
        assertFalse(input.closed);

        assertThrows(IllegalArgumentException.class,
                () -> ExcelUtils.readRows(input(output.toByteArray()), new ExcelReadOptions(1, 1, 500)));
    }

    @Test
    void supportsLegacyXlsInput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output).excelType(ExcelTypeEnum.XLS).head(List.of(List.of("姓名")))
                .sheet("人员").doWrite(List.of(List.of("张三")));

        assertEquals(List.of(Map.of(0, "张三")), ExcelUtils.readRows(input(output.toByteArray())));
    }

    @Test
    void deliversIndependentBatchesIncludingFinalPartialBatch() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.write(output, TestRow.class, testRows(5));
        List<List<TestRow>> batches = new ArrayList<>();

        long count = ExcelUtils.readBatches(input(output.toByteArray()), TestRow.class,
                new ExcelReadOptions(0, 1, 2), batches::add);

        assertEquals(5, count);
        assertEquals(List.of(2, 2, 1), batches.stream().map(List::size).toList());
        assertNotSame(batches.get(0), batches.get(1));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L),
                batches.stream().flatMap(List::stream).map(TestRow::getId).toList());
    }

    @Test
    void batchesDynamicRowsWithoutAnEmptyTrailingCallback() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("姓名")),
                List.of(List.of("甲"), List.of("乙"), List.of("丙"), List.of("丁")), "数据");
        List<List<Map<Integer, String>>> batches = new ArrayList<>();

        long count = ExcelUtils.readRowsBatches(input(output.toByteArray()),
                new ExcelReadOptions(0, 1, 2), batches::add);

        assertEquals(4, count);
        assertEquals(List.of(2, 2), batches.stream().map(List::size).toList());
        assertEquals("甲", batches.get(0).get(0).get(0));
        assertEquals("丁", batches.get(1).get(1).get(0));
    }

    @Test
    void writesLazyBatchesInOrderAndEmitsHeaderOnlyOnce() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicInteger fetched = new AtomicInteger();
        Iterable<List<TestRow>> batches = () -> new Iterator<>() {
            private int next = 1;

            @Override
            public boolean hasNext() {
                return next <= 3;
            }

            @Override
            public List<TestRow> next() {
                fetched.incrementAndGet();
                return List.of(new TestRow((long) next++, "姓名", BigDecimal.ONE, LocalDate.of(2026, 10, 8)));
            }
        };

        ExcelUtils.writeBatches(output, TestRow.class, batches, "分页导出");

        assertEquals(3, fetched.get());
        assertEquals(List.of(1L, 2L, 3L),
                ExcelUtils.read(input(output.toByteArray()), TestRow.class).stream().map(TestRow::getId).toList());
    }

    @Test
    void exportsTemplatesForEmptyRowsAndForNoBatches() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.write(output, TestRow.class, List.of());
        List<Map<Integer, String>> heads = new ArrayList<>();
        List<TestRow> rows = new ArrayList<>();
        ExcelUtils.readWithListener(input(output.toByteArray()), TestRow.class, ExcelReadOptions.defaults(),
                new AnalysisEventListener<TestRow>() {
                    @Override
                    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                        heads.add(headMap);
                    }

                    @Override
                    public void invoke(TestRow data, AnalysisContext context) {
                        rows.add(data);
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                    }
                });
        assertTrue(rows.isEmpty());
        assertEquals(List.of(Map.of(0, "编号", 1, "姓名", 2, "金额", 3, "日期")), heads);

        AtomicInteger callbacks = new AtomicInteger();
        assertEquals(0, ExcelUtils.readBatches(input(output.toByteArray()), TestRow.class,
                ExcelReadOptions.defaults(), batch -> callbacks.incrementAndGet()));
        assertEquals(0, callbacks.get());

        ByteArrayOutputStream emptyBatchesOutput = new ByteArrayOutputStream();
        ExcelUtils.writeBatches(emptyBatchesOutput, TestRow.class, Collections.emptyList(), "模板");
        assertEquals(List.of(Map.of(0, "编号", 1, "姓名", 2, "金额", 3, "日期")),
                ExcelUtils.readRows(input(emptyBatchesOutput.toByteArray()), new ExcelReadOptions(0, 0, 500)));
    }

    @Test
    void propagatesConversionErrorWithCellLocationAndLeavesInputOpen() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("编号"), List.of("姓名"), List.of("金额"), List.of("日期")),
                List.of(List.of("1", "张三", "非法金额", "2026-10-08")), "数据");
        TrackingInputStream input = new TrackingInputStream(output.toByteArray());

        ExcelDataConvertException error = assertThrows(ExcelDataConvertException.class,
                () -> ExcelUtils.read(input, TestRow.class));

        assertEquals(1, error.getRowIndex());
        assertEquals(2, error.getColumnIndex());
        assertFalse(input.closed);
    }

    @Test
    void stopsImmediatelyOnBatchConsumerFailureAndLeavesInputOpen() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.write(output, TestRow.class, testRows(5));
        TrackingInputStream input = new TrackingInputStream(output.toByteArray());
        AtomicInteger callbacks = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("业务校验失败");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> ExcelUtils.readBatches(input, TestRow.class, new ExcelReadOptions(0, 1, 2), batch -> {
                    callbacks.incrementAndGet();
                    throw failure;
                }));

        assertEquals(failure, rootCause(error));
        assertEquals(1, callbacks.get());
        assertFalse(input.closed);
    }

    @Test
    void leavesOutputOpenWhenFetchingExportDataFails() {
        TrackingOutputStream output = new TrackingOutputStream();
        IllegalStateException failure = new IllegalStateException("查询失败");
        Iterable<List<TestRow>> failingBatches = () -> {
            throw failure;
        };

        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> ExcelUtils.writeBatches(output, TestRow.class, failingBatches, "数据")));
        assertFalse(output.closed);
    }

    @Test
    void rejectsInvalidConfigurationAndHeadersBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> new ExcelReadOptions(-1, 1, 500));
        assertThrows(IllegalArgumentException.class, () -> new ExcelReadOptions(0, -1, 500));
        assertThrows(IllegalArgumentException.class, () -> new ExcelReadOptions(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExcelReadOptions(0, 1, -1));

        TrackingOutputStream output = new TrackingOutputStream();
        assertThrows(IllegalArgumentException.class,
                () -> ExcelUtils.write(output, TestRow.class, List.of(), "非法/名称"));
        assertThrows(IllegalArgumentException.class,
                () -> ExcelUtils.writeRows(output, List.of(), List.of(), "数据"));
        assertThrows(IllegalArgumentException.class,
                () -> ExcelUtils.writeRows(output, List.of(List.of()), List.of(), "数据"));
        assertEquals(0, output.size());
        assertFalse(output.closed);
    }

    private static ByteArrayInputStream input(byte[] content) {
        return new ByteArrayInputStream(content);
    }

    private static List<TestRow> testRows(int count) {
        List<TestRow> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new TestRow((long) i, "姓名" + i, BigDecimal.ONE, LocalDate.of(2026, 10, 8)));
        }
        return rows;
    }

    private static Throwable rootCause(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    public static class TestRow {
        @ExcelProperty(value = "编号", index = 0, converter = LongStringConverter.class)
        private Long id;
        @ExcelProperty(value = "姓名", index = 1)
        private String name;
        @ExcelProperty(value = "金额", index = 2)
        private BigDecimal amount;
        @ExcelProperty(value = "日期", index = 3)
        @DateTimeFormat("yyyy-MM-dd")
        private LocalDate day;
        @ExcelIgnore
        private String internalValue = "不应导出";

        public TestRow() {
        }

        public TestRow(Long id, String name, BigDecimal amount, LocalDate day) {
            this.id = id;
            this.name = name;
            this.amount = amount;
            this.day = day;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDate getDay() { return day; }
        public void setDay(LocalDate day) { this.day = day; }
        public String getInternalValue() { return internalValue; }
        public void setInternalValue(String internalValue) { this.internalValue = internalValue; }
    }
}
