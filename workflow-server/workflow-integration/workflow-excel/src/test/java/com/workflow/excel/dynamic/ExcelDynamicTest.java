package com.workflow.excel.dynamic;

import com.workflow.excel.ExcelReadOptions;
import com.workflow.excel.ExcelUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.hssf.record.DateWindow1904Record;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ExcelDynamicTest {
    private static final LocalDateTime DATE = LocalDateTime.of(2024, 2, 29, 12, 30, 15);

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void preservesNativeCellsCoordinatesAndBothDateSystems(boolean xls, boolean use1904) throws Exception {
        byte[] file = workbook(xls, use1904);
        List<ExcelRawRow> headers = new ArrayList<>();
        List<List<ExcelRawRow>> batches = new ArrayList<>();
        TrackingInput input = new TrackingInput(file);
        long count = ExcelUtils.readRawRows(input, new ExcelReadOptions(0, 1, 1), 20, headers::add, batches::add);
        assertFalse(input.closed);
        assertEquals(1, headers.size());
        assertEquals("测试", headers.get(0).sheetName());
        assertEquals(1, headers.get(0).rowNumber());
        ExcelRawRow row = batches.stream().flatMap(Collection::stream).filter(r -> r.rowIndex() == 3).findFirst().orElseThrow();
        assertEquals(4, row.rowNumber());
        assertEquals(use1904, row.use1904Windowing());
        assertEquals("  00123  ", row.cells().get(0).value());
        assertEquals(ExcelCellType.STRING, row.cells().get(0).type());
        assertEquals("  00123  ", ExcelValueCodec.readString(row.cells().get(0), false, false));
        assertEquals("00123", ExcelValueCodec.readString(row.cells().get(0), true, true));
        assertEquals(ExcelCellType.NUMBER, row.cells().get(1).type());
        assertEquals("00000", row.cells().get(1).format());
        assertEquals("00123", ExcelValueCodec.readString(row.cells().get(1), true, true));
        assertEquals(DATE, ExcelValueCodec.readDateTime(row.cells().get(2), "", List.of()));
        assertEquals(Boolean.TRUE, row.cells().get(3).value());
        assertEquals(ExcelCellType.ERROR, row.cells().get(4).type());
        assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readString(row.cells().get(4), true, true));
        assertTrue(count >= 1);
        assertThrows(UnsupportedOperationException.class, () -> row.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> batches.get(0).clear());
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void exposesEveryFormulaWithoutEvaluatingCachedNumbersOrStrings(boolean xls) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("公式");
            sheet.createRow(0).createCell(0).setCellValue("输入");
            var row = sheet.createRow(1);
            row.createCell(0).setCellFormula("1+1");
            row.createCell(1).setCellFormula("\"text\"");
            row.createCell(2).setCellFormula("\"\"");
            sheet.createRow(4).createCell(0).setCellFormula("\"\"");
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("00000"));
            sheet.getRow(4).getCell(0).setCellStyle(style);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
        }
        List<ExcelRawRow> rows = new ArrayList<>();
        ExcelUtils.readRawRows(new ByteArrayInputStream(output.toByteArray()), ExcelReadOptions.defaults(), 10, h -> {}, rows::addAll);
        var populated = rows.stream().filter(row -> !row.isBlank()).toList();
        assertEquals(2, populated.size());
        assertEquals(5, populated.get(1).rowNumber());
        assertEquals(ExcelCellType.FORMULA, populated.get(1).cells().get(0).type());
        assertEquals("00000", populated.get(1).cells().get(0).format());
        assertNotNull(populated.get(1).cells().get(0).formatIndex());
        for (int column = 0; column < 3; column++) {
            var cell = rows.get(0).cells().get(column);
            assertEquals(ExcelCellType.FORMULA, cell.type());
            assertFalse(cell.isBlank());
            assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readString(cell, true, true));
            assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readAmount(cell, 2, RoundingMode.UNNECESSARY, true, ""));
            assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readDate(cell, "", List.of()));
            assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readCode(cell, Map.of("1", "是"), true, true));
        }
    }

    @Test
    void boundedReadingStopsBeforeDeliveringTheOverflowRowAndRetainsCallerStream() {
        TrackingOutput output = new TrackingOutput();
        ExcelUtils.writeRows(output, List.of(List.of("编号")), List.of(List.of("a"), List.of("b"), List.of("c")), "数据");
        TrackingInput input = new TrackingInput(output.toByteArray());
        List<ExcelRawRow> rows = new ArrayList<>();
        RuntimeException error = assertThrows(RuntimeException.class, () -> ExcelUtils.readRawRows(input,
                new ExcelReadOptions(0, 1, 1), 2, h -> {}, rows::addAll));
        assertTrue(error.toString().contains("上限"));
        // 元数据预扫已发现超限时，不向业务发布部分数据。
        assertTrue(rows.isEmpty());
        assertFalse(input.closed);
        assertFalse(output.closed);
        assertThrows(IllegalArgumentException.class, () -> ExcelUtils.readRawRows(new ByteArrayInputStream(output.toByteArray()),
                new ExcelReadOptions(2, 1, 1), 10, h -> {}, batch -> {}));
    }

    @Test
    void codecsValidateStrictDatesAmountsAndUnambiguousCodes() {
        assertEquals(LocalDate.of(2024, 2, 29), ExcelValueCodec.readDate(text("2024/02/29"), "yyyy-MM-dd", List.of("yyyy/MM/dd")));
        assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readDate(text("2025-02-29"), "", List.of()));
        assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readDateTime(text("2024-02-29T00:00:00+08:00"), "yyyy-MM-dd'T'HH:mm:ssXXX", List.of()));
        assertEquals(new BigDecimal("1234.50"), ExcelValueCodec.readAmount(text("￥1,234.50"), 2, RoundingMode.UNNECESSARY, true, "￥"));
        assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readAmount(text("12,34.50"), 2, RoundingMode.UNNECESSARY, true, ""));
        assertThrows(ArithmeticException.class, () -> ExcelValueCodec.readAmount(text("1.235"), 2, RoundingMode.UNNECESSARY, true, ""));
        assertEquals("1234567890123456.78", ExcelValueCodec.writeAmount(new BigDecimal("1234567890123456.78"), 2, RoundingMode.UNNECESSARY, true));
        assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.writeAmount(new BigDecimal("1234567890123456.78"), 2, RoundingMode.UNNECESSARY, false));
        var codes = Map.of("01", "在职", "02", "离职");
        assertEquals("01", ExcelValueCodec.readCode(text("在职"), codes, false, true));
        assertEquals("01", ExcelValueCodec.readCode(text("01"), codes, true, true));
        assertThrows(IllegalArgumentException.class, () -> ExcelValueCodec.readCode(text("01"), codes, false, true));
        assertThrows(IllegalArgumentException.class, () -> new ExcelValueCodec.CodeMapping(Map.of("1", "同名", "2", "同名"), true, true));
        assertThrows(IllegalArgumentException.class, () -> new ExcelValueCodec.CodeMapping(Map.of("1", "2", "2", "其他"), true, true));
        assertEquals("未知", ExcelValueCodec.readCode(text("未知"), codes, true, false));
        assertEquals("在职", ExcelValueCodec.writeCode("01", codes, true));
    }

    @Test
    void lazyDynamicBatchesWriteOneHeaderAndLiteralTextWithoutClosingOutput() throws Exception {
        AtomicInteger requested = new AtomicInteger();
        Iterable<Collection<? extends List<?>>> batches = () -> new Iterator<>() {
            private int next;
            public boolean hasNext() { return next < 3; }
            public Collection<? extends List<?>> next() {
                requested.incrementAndGet();
                next++;
                return List.of(List.of("=1+1", "00" + next, ExcelValueCodec.writeAmount(new BigDecimal("9999999999999999.99"), 2, RoundingMode.UNNECESSARY, true)));
            }
        };
        TrackingOutput output = new TrackingOutput();
        ExcelUtils.writeRowsBatches(output, List.of(List.of("文本"), List.of("编码"), List.of("金额")), batches, "导出");
        assertEquals(3, requested.get());
        assertFalse(output.closed);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum());
            assertEquals("文本", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(0).getCellType());
            assertEquals("=1+1", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("003", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("9999999999999999.99", sheet.getRow(2).getCell(2).getStringCellValue());
        }
    }

    private static ExcelRawCell text(String text) { return new ExcelRawCell(0, ExcelCellType.STRING, text, null, null, false); }

    private byte[] workbook(boolean xls, boolean use1904) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            if (workbook instanceof XSSFWorkbook xlsx) xlsx.getCTWorkbook().getWorkbookPr().setDate1904(use1904);
            else ((DateWindow1904Record) ((HSSFWorkbook) workbook).getInternalWorkbook()
                    .findFirstRecordBySid(DateWindow1904Record.sid)).setWindowing(use1904 ? (short) 1 : 0);
            var sheet = workbook.createSheet("测试");
            sheet.createRow(0).createCell(0).setCellValue("表头");
            sheet.createRow(1).createCell(0).setBlank();
            var row = sheet.createRow(3);
            row.createCell(0).setCellValue("  00123  ");
            row.createCell(1).setCellValue(123);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("00000"));
            row.getCell(1).setCellStyle(style);
            row.createCell(2).setCellValue(DateUtil.getExcelDate(DATE, use1904));
            row.createCell(3).setCellValue(true);
            row.createCell(4).setCellErrorValue(FormulaError.DIV0.getCode());
            workbook.write(output);
        }
        return output.toByteArray();
    }

    private static class TrackingInput extends ByteArrayInputStream {
        private boolean closed;
        TrackingInput(byte[] bytes) { super(bytes); }
        public void close() { closed = true; }
    }

    private static class TrackingOutput extends ByteArrayOutputStream {
        private boolean closed;
        public void close() { closed = true; }
    }
}
