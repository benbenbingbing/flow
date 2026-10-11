package com.workflow.excel.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.excel.annotation.ExcelProperty;
import com.workflow.excel.ExcelReadOptions;
import com.workflow.excel.ExcelUtils;
import com.workflow.excel.annotation.validation.ExcelEmail;
import com.workflow.excel.annotation.validation.ExcelMin;
import com.workflow.excel.annotation.validation.ExcelNotBlank;
import com.workflow.excel.annotation.validation.ExcelNotNull;
import com.workflow.excel.annotation.validation.ExcelPattern;
import com.workflow.excel.validation.ExcelValidationException;
import jakarta.validation.groups.Default;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelListenersTest {
    @Test
    void defaultReadValidatesAnnotatedFields() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.write(output, ImportRow.class, List.of(new ImportRow(0L, " ", "wrong")), "人员");

        ExcelValidationException error = assertThrows(ExcelValidationException.class,
                () -> ExcelUtils.read(input(output), ImportRow.class));

        assertEquals("人员", error.getSheetName());
        assertEquals(1, error.getRowIndex());
        assertEquals(List.of("email", "id", "name"),
                error.getErrors().stream().map(value -> value.fieldName()).toList());
    }

    @Test
    void reportsPhysicalRowAfterMultipleHeadersAndBlankRowsWithoutDeliveringInvalidRow() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("人员", "编号"), List.of("人员", "姓名"), List.of("人员", "邮箱")),
                List.of(List.of("1", "张三", "user@example.com"), List.of("", "", ""), List.of("0", " ", "wrong")), "人员");
        List<List<ImportRow>> batches = new ArrayList<>();

        ExcelValidationException error = assertThrows(ExcelValidationException.class,
                () -> ExcelUtils.readBatches(input(output), ImportRow.class, new ExcelReadOptions(0, 2, 1), batches::add));

        assertEquals(4, error.getRowIndex());
        assertEquals(1, batches.size());
        assertEquals(1L, batches.get(0).get(0).getId());
        assertTrue(error.getMessage().contains("第 5 行"));
    }

    @Test
    void publicReadListenerRetainsHeadersAndSupportsExplicitGroups() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.write(output, ImportRow.class,
                List.of(new ImportRow(1L, "alice", "user@example.com"), new ImportRow(2L, "Bob", "user@example.com")), "人员");
        List<List<ImportRow>> batches = new ArrayList<>();
        ExcelReadListener<ImportRow> listener = new ExcelReadListener<>(1, batches::add, Default.class, StrictImport.class);

        ExcelValidationException error = assertThrows(ExcelValidationException.class,
                () -> ExcelUtils.readWithListener(input(output), ImportRow.class, ExcelReadOptions.defaults(), listener));

        assertEquals(2, error.getRowIndex());
        assertEquals("姓名必须为小写字母", error.getErrors().get(0).message());
        assertEquals(1, listener.getRowCount());
        assertEquals(1, batches.size());
        assertEquals(List.of(Map.of(0, "编号", 1, "姓名", 2, "邮箱")), listener.getHeadRows());
        assertThrows(UnsupportedOperationException.class, () -> listener.getHeadRows().clear());
        assertThrows(UnsupportedOperationException.class, () -> listener.getHeadRows().get(0).clear());
    }

    @Test
    void dynamicReadListenerCollectsMultiLevelHeadersAndFinalPartialBatch() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("人员", "姓名"), List.of("人员", "编号")),
                List.of(List.of("张三", "001"), List.of("李四", "002"), List.of("王五", "003")), "人员");
        List<List<Map<Integer, String>>> batches = new ArrayList<>();
        ExcelReadListener<Map<Integer, String>> listener = new ExcelReadListener<>(2, batches::add);

        ExcelUtils.readRowsWithListener(input(output), new ExcelReadOptions(0, 2, 2), listener);

        assertEquals(3, listener.getRowCount());
        assertEquals(List.of(2, 1), batches.stream().map(List::size).toList());
        assertEquals(2, listener.getHeadRows().size());
        assertEquals("姓名", listener.getHeadRows().get(1).get(0));
    }

    @Test
    void writeListenerSpansAllBatchesSkipsHeadersAndCanAdjustRowStyle() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Integer> rowIndexes = new ArrayList<>();
        ExcelWriteListener listener = new ExcelWriteListener(context -> {
            rowIndexes.add(context.getRowIndex());
            context.getRow().setHeightInPoints(25);
        });

        ExcelUtils.writeBatches(output, ImportRow.class,
                List.of(List.of(new ImportRow(1L, "alice", "user@example.com")), List.of(),
                        List.of(new ImportRow(2L, "bob", "user@example.com"))), "人员", listener);

        assertEquals(2, listener.getRowCount());
        assertEquals(List.of(1, 2), rowIndexes);
        try (XSSFWorkbook workbook = new XSSFWorkbook(input(output))) {
            assertEquals(25, workbook.getSheetAt(0).getRow(1).getHeightInPoints());
            assertEquals(25, workbook.getSheetAt(0).getRow(2).getHeightInPoints());
        }
    }

    @Test
    void writeListenerSkipsAllMultiLevelHeadersAndEmptyTemplates() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Integer> rowIndexes = new ArrayList<>();
        ExcelWriteListener listener = new ExcelWriteListener(context -> rowIndexes.add(context.getRowIndex()));
        ExcelUtils.writeRows(output, List.of(List.of("人员", "姓名")), List.of(List.of("张三")), "人员", listener);
        assertEquals(1, listener.getRowCount());
        assertEquals(List.of(2), rowIndexes);

        ExcelWriteListener emptyListener = new ExcelWriteListener();
        ExcelUtils.write(new ByteArrayOutputStream(), ImportRow.class, List.of(), "模板", emptyListener);
        assertEquals(0, emptyListener.getRowCount());
    }

    @Test
    void writeCallbackFailureStopsExportAndKeepsCallerStreamOpen() {
        TrackingOutputStream output = new TrackingOutputStream();
        IllegalStateException failure = new IllegalStateException("回调失败");
        ExcelWriteListener listener = new ExcelWriteListener(context -> {
            throw failure;
        });

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> ExcelUtils.write(output, ImportRow.class,
                        List.of(new ImportRow(1L, "alice", "user@example.com")), "人员", listener));
        while (error.getCause() instanceof RuntimeException cause) {
            error = cause;
        }
        assertEquals(failure, error);
        assertFalse(output.closed);
    }

    private static ByteArrayInputStream input(ByteArrayOutputStream output) {
        return new ByteArrayInputStream(output.toByteArray());
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    public interface StrictImport {
    }

    public static class ImportRow {
        @ExcelProperty(value = "编号", index = 0)
        @ExcelNotNull
        @ExcelMin("1")
        private Long id;
        @ExcelProperty(value = "姓名", index = 1)
        @ExcelNotBlank
        @ExcelPattern(regexp = "[a-z]+", message = "姓名必须为小写字母", groups = StrictImport.class)
        private String name;
        @ExcelProperty(value = "邮箱", index = 2)
        @ExcelEmail
        private String email;

        public ImportRow() {
        }

        public ImportRow(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
