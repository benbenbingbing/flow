package com.workflow.excel.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.workflow.excel.ExcelReadOptions;
import com.workflow.excel.ExcelUtils;
import com.workflow.excel.annotation.converter.ExcelAmountFormat;
import com.workflow.excel.annotation.validation.ExcelCode;
import com.workflow.excel.annotation.converter.ExcelCodeMapping;
import com.workflow.excel.annotation.converter.ExcelDateFormat;
import com.workflow.excel.annotation.validation.ExcelNotBlank;
import com.workflow.excel.annotation.converter.ExcelStringFormat;
import com.workflow.excel.validation.ExcelValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.hssf.record.DateWindow1904Record;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelConvertersTest {
    private static final GlobalConfiguration GLOBAL = new GlobalConfiguration();

    @Test
    void roundTripsAllConvertersWithoutLosingDateTimeOrAmountPrecision() throws Exception {
        ConvertedRow source = new ConvertedRow();
        source.setDay(LocalDate.of(2026, 10, 8));
        source.setTime(LocalDateTime.of(2026, 10, 8, 12, 30, 15, 123456789));
        source.setLegacy(Date.from(Instant.parse("2026-10-08T01:02:03.123Z")));
        source.setIdentifier("  001234567890123456789  ");
        source.setAmount(new BigDecimal("12345678901234567890.12"));
        source.setStatus(1);
        source.setCategory("001");
        source.setState(State.ACTIVE);
        source.setEnabled(Boolean.TRUE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ExcelUtils.write(output, ConvertedRow.class, List.of(source), "转换");
        ConvertedRow target = ExcelUtils.read(input(output), ConvertedRow.class).get(0);

        assertEquals(source.getDay(), target.getDay());
        assertEquals(source.getTime(), target.getTime());
        assertEquals(source.getLegacy(), target.getLegacy());
        assertEquals("001234567890123456789", target.getIdentifier());
        assertEquals(source.getAmount(), target.getAmount());
        assertEquals(source.getStatus(), target.getStatus());
        assertEquals(source.getCategory(), target.getCategory());
        assertEquals(source.getState(), target.getState());
        assertEquals(source.getEnabled(), target.getEnabled());
        Map<Integer, String> text = ExcelUtils.readRows(input(output)).get(0);
        assertEquals("08/10/2026", text.get(0));
        assertEquals("2026-10-08 12:30:15.123456789", text.get(1));
        assertEquals("2026-10-08 01:02:03.123", text.get(2));
        assertEquals("启用", text.get(5));
        assertEquals("一般", text.get(6));
        assertEquals("有效", text.get(7));
        assertEquals("是", text.get(8));
        try (XSSFWorkbook workbook = new XSSFWorkbook(input(output))) {
            assertEquals(CellType.STRING, workbook.getSheetAt(0).getRow(1).getCell(3).getCellType());
            assertEquals(CellType.STRING, workbook.getSheetAt(0).getRow(1).getCell(4).getCellType());
        }
    }

    @Test
    void readsGroupedCurrencyAmountsAndConfiguredRawCodes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, headers(),
                List.of(List.of("2026-10-08", "2026/10/08 12:30:15.125", "2026-10-08", "00123",
                        "￥1,234.50", "1", "001", "ACTIVE", "true")), "转换");

        ConvertedRow row = ExcelUtils.read(input(output), ConvertedRow.class).get(0);

        assertEquals(LocalDate.of(2026, 10, 8), row.getDay());
        assertEquals(LocalDateTime.of(2026, 10, 8, 12, 30, 15, 125000000), row.getTime());
        assertEquals(Date.from(Instant.parse("2026-10-08T00:00:00Z")), row.getLegacy());
        assertEquals(new BigDecimal("1234.50"), row.getAmount());
        assertEquals(1, row.getStatus());
        assertEquals("001", row.getCategory());
        assertEquals(State.ACTIVE, row.getState());
        assertEquals(Boolean.TRUE, row.getEnabled());
    }

    @Test
    void readsNativeExcelDatesUsingBothWorkbookDateSystems() throws Exception {
        LocalDateTime expected = LocalDateTime.of(2024, 2, 29, 12, 30, 15);
        for (boolean use1904 : List.of(false, true)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                workbook.getCTWorkbook().getWorkbookPr().setDate1904(use1904);
                var sheet = workbook.createSheet("原生日期");
                var head = sheet.createRow(0);
                head.createCell(0).setCellValue("日期");
                head.createCell(1).setCellValue("时间");
                head.createCell(2).setCellValue("旧日期");
                var row = sheet.createRow(1);
                CellStyle style = workbook.createCellStyle();
                style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
                for (int column = 0; column < 3; column++) {
                    var cell = row.createCell(column);
                    cell.setCellValue(DateUtil.getExcelDate(expected, use1904));
                    cell.setCellStyle(style);
                }
                workbook.write(output);
            }

            NativeDateRow row = ExcelUtils.read(input(output), NativeDateRow.class).get(0);
            assertEquals(expected.toLocalDate(), row.getDay());
            assertEquals(expected, row.getTime());
            assertEquals(Date.from(expected.toInstant(java.time.ZoneOffset.UTC)), row.getLegacy());
        }
    }

    @Test
    void readsLegacyXlsDatesUsingBothWorkbookDateSystems() throws Exception {
        LocalDateTime expected = LocalDateTime.of(2024, 2, 29, 12, 30, 15);
        for (boolean use1904 : List.of(false, true)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (HSSFWorkbook workbook = new HSSFWorkbook()) {
                DateWindow1904Record record = (DateWindow1904Record) workbook.getInternalWorkbook()
                        .findFirstRecordBySid(DateWindow1904Record.sid);
                record.setWindowing(use1904 ? (short) 1 : 0);
                var sheet = workbook.createSheet("原生日期");
                var head = sheet.createRow(0);
                head.createCell(0).setCellValue("日期");
                head.createCell(1).setCellValue("时间");
                head.createCell(2).setCellValue("旧日期");
                var row = sheet.createRow(1);
                for (int column = 0; column < 3; column++) {
                    row.createCell(column).setCellValue(DateUtil.getExcelDate(expected, use1904));
                }
                workbook.write(output);
            }
            NativeDateRow row = ExcelUtils.read(input(output), NativeDateRow.class).get(0);
            assertEquals(expected.toLocalDate(), row.getDay());
            assertEquals(expected, row.getTime());
            assertEquals(Date.from(expected.toInstant(java.time.ZoneOffset.UTC)), row.getLegacy());
        }
    }

    @Test
    void rejectsImpossibleDatesWithOriginalCellLocation() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("日期")), List.of(List.of("2026-02-30")), "日期");

        ExcelDataConvertException error = assertThrows(ExcelDataConvertException.class,
                () -> ExcelUtils.read(input(output), NativeDateRow.class));
        assertEquals(1, error.getRowIndex());
        assertEquals(0, error.getColumnIndex());
    }

    @Test
    void rejectsAmbiguousOrMissingLegacyDateTimeInConfiguredZone() throws Exception {
        ExcelDateConverter converter = new ExcelDateConverter();
        ExcelContentProperty property = property("newYork");
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("2026-03-08 02:30:00"), property, GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("2026-11-01 01:30:00"), property, GLOBAL));
    }

    @Test
    void refusesToDiscardOffsetProvidedInLocalDateTimeText() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new ExcelLocalDateTimeConverter().convertToJavaData(
                        new ReadCellData<>("2026-10-08T12:30:00+08:00"), property("offsetTime"), GLOBAL));
    }

    @Test
    void amountRoundingIsExplicitAndMalformedGroupingIsRejected() throws Exception {
        ExcelAmountConverter converter = new ExcelAmountConverter();
        assertThrows(ArithmeticException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("1.005"), null, GLOBAL));
        assertEquals(new BigDecimal("1.01"),
                converter.convertToJavaData(new ReadCellData<>("1.005"), property("rounded"), GLOBAL));
        assertEquals(new BigDecimal("1234.50"),
                converter.convertToJavaData(new ReadCellData<>("￥1,234.50"), property("currency"), GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("1,23.45"), null, GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("$123.45"), property("currency"), GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("1,234.50"), property("ungrouped"), GLOBAL));
        assertEquals(new BigDecimal("12.50"),
                converter.convertToJavaData(new ReadCellData<>(new BigDecimal("12.5")), null, GLOBAL));
    }

    @Test
    void numericAmountExportIsOptInAndRejectsValuesItCannotRepresentSafely() throws Exception {
        ExcelAmountConverter converter = new ExcelAmountConverter();
        assertEquals(CellDataTypeEnum.NUMBER,
                converter.convertToExcelData(new BigDecimal("1234.50"), property("numeric"), GLOBAL).getType());
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToExcelData(new BigDecimal("1234567890123456.78"), property("numeric"), GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToExcelData(new BigDecimal("1E+400"), property("numeric"), GLOBAL));
        assertEquals("1234567890123456.78",
                converter.convertToExcelData(new BigDecimal("1234567890123456.78"), null, GLOBAL).getStringValue());
    }

    @Test
    void stringConverterPreservesWhitespaceWhenRequestedAndCleansOtherFields() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("保留"), List.of("清理"), List.of("空文本")),
                List.of(List.of("  001  ", "  002  ", "   ")), "字符串");

        StringRow row = ExcelUtils.read(input(output), StringRow.class, new ExcelReadOptions(0, 1, 500, false)).get(0);

        assertEquals("  001  ", row.getPreserved());
        assertEquals("002", row.getCleaned());
        assertNull(row.getBlank());
    }

    @Test
    void stringCleaningRunsBeforeRequiredFieldValidation() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, headers(),
                List.of(List.of("2026-10-08", "2026-10-08", "2026-10-08", " ", "1.00", "启用", "一般", "有效", "是")), "转换");

        ExcelValidationException error = assertThrows(ExcelValidationException.class,
                () -> ExcelUtils.read(input(output), ConvertedRow.class));
        assertEquals("identifier", error.getErrors().get(0).fieldName());
    }

    @Test
    void numericCodesIgnoreDecimalStyleAndStringCodesRetainDisplayZeros() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("代码");
            sheet.createRow(0).createCell(0).setCellValue("整数代码");
            sheet.getRow(0).createCell(1).setCellValue("字符串代码");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue(123);
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            row.getCell(0).setCellStyle(decimalStyle);
            CellStyle zeroStyle = workbook.createCellStyle();
            zeroStyle.setDataFormat(workbook.createDataFormat().getFormat("00000"));
            row.getCell(1).setCellStyle(zeroStyle);
            workbook.write(output);
        }

        NumericCodeRow row = ExcelUtils.read(input(output), NumericCodeRow.class).get(0);
        assertEquals(1, row.getNumeric());
        assertEquals("00123", row.getText());
    }

    @Test
    void codeMappingsRejectUnknownValuesUnlessPassThroughIsExplicit() throws Exception {
        ExcelCodeConverter converter = new ExcelCodeConverter();
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("未知"), property("strictCode"), GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToExcelData("X", property("strictCode"), GLOBAL));
        assertEquals("X", converter.convertToJavaData(new ReadCellData<>("X"), property("relaxedCode"), GLOBAL));
        assertEquals("X", converter.convertToExcelData("X", property("relaxedCode"), GLOBAL).getStringValue());
        assertEquals("1", converter.convertToJavaData(new ReadCellData<>("启用"), property("labelOnly"), GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToJavaData(new ReadCellData<>("1"), property("labelOnly"), GLOBAL));
    }

    @Test
    void codeMappingsRejectDuplicateOrAmbiguousDefinitions() throws Exception {
        ExcelCodeConverter converter = new ExcelCodeConverter();
        for (String field : List.of("duplicateLabel", "ambiguousLabel", "duplicateValue")) {
            ExcelContentProperty property = property(field);
            assertThrows(IllegalArgumentException.class,
                    () -> converter.convertToJavaData(new ReadCellData<>("A"), property, GLOBAL));
        }
    }

    @Test
    void convertersTreatEmptyCellsAsNullAndRejectUnsupportedDateCells() {
        assertNull(new ExcelAmountConverter().convertToJavaData(ReadCellData.newEmptyInstance(), null, GLOBAL));
        assertNull(new ExcelLocalDateConverter().convertToJavaData(ReadCellData.newEmptyInstance(), null, GLOBAL));
        assertNull(new ExcelStringConverter().convertToJavaData(ReadCellData.newEmptyInstance(), null, GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> new ExcelLocalDateConverter().convertToJavaData(new ReadCellData<>(true), null, GLOBAL));
        assertTrue(new ExcelLocalDateTimeConverter().convertToExcelData(null, null, GLOBAL).getType() == CellDataTypeEnum.EMPTY);
    }

    private static ByteArrayInputStream input(ByteArrayOutputStream output) {
        return new ByteArrayInputStream(output.toByteArray());
    }

    private static ExcelContentProperty property(String field) throws NoSuchFieldException {
        ExcelContentProperty property = new ExcelContentProperty();
        property.setField(Settings.class.getDeclaredField(field));
        return property;
    }

    private static List<List<String>> headers() {
        return List.of(List.of("日期"), List.of("时间"), List.of("旧日期"), List.of("标识"), List.of("金额"),
                List.of("状态"), List.of("分类"), List.of("枚举"), List.of("启用"));
    }

    private static class Settings {
        @ExcelAmountFormat(roundingMode = RoundingMode.HALF_UP)
        private BigDecimal rounded;
        @ExcelAmountFormat(currencySymbol = "￥")
        private BigDecimal currency;
        @ExcelAmountFormat(allowThousands = false)
        private BigDecimal ungrouped;
        @ExcelAmountFormat(writeAsText = false)
        private BigDecimal numeric;
        @ExcelDateFormat(zoneId = "America/New_York")
        private Date newYork;
        @ExcelDateFormat(readPatterns = "yyyy-MM-dd'T'HH:mm:ssXXX")
        private LocalDateTime offsetTime;
        @ExcelCodeMapping(@ExcelCode(code = "1", label = "启用"))
        private String strictCode;
        @ExcelCodeMapping(value = @ExcelCode(code = "1", label = "启用"), strict = false)
        private String relaxedCode;
        @ExcelCodeMapping(value = @ExcelCode(code = "1", label = "启用"), allowCode = false)
        private String labelOnly;
        @ExcelCodeMapping({@ExcelCode(code = "A", label = "名称"), @ExcelCode(code = "B", label = "名称")})
        private String duplicateLabel;
        @ExcelCodeMapping({@ExcelCode(code = "A", label = "B"), @ExcelCode(code = "B", label = "名称")})
        private String ambiguousLabel;
        @ExcelCodeMapping({@ExcelCode(code = "01", label = "甲"), @ExcelCode(code = "1", label = "乙")})
        private Integer duplicateValue;
    }

    public enum State { ACTIVE, INACTIVE }

    public static class ConvertedRow {
        @ExcelProperty(value = "日期", index = 0, converter = ExcelLocalDateConverter.class)
        @ExcelDateFormat(value = "dd/MM/yyyy", readPatterns = {"yyyy-MM-dd"})
        private LocalDate day;
        @ExcelProperty(value = "时间", index = 1, converter = ExcelLocalDateTimeConverter.class)
        private LocalDateTime time;
        @ExcelProperty(value = "旧日期", index = 2, converter = ExcelDateConverter.class)
        @ExcelDateFormat(zoneId = "UTC")
        private Date legacy;
        @ExcelProperty(value = "标识", index = 3, converter = ExcelStringConverter.class)
        @ExcelNotBlank
        private String identifier;
        @ExcelProperty(value = "金额", index = 4, converter = ExcelAmountConverter.class)
        @ExcelAmountFormat(currencySymbol = "￥")
        private BigDecimal amount;
        @ExcelProperty(value = "状态", index = 5, converter = ExcelCodeConverter.class)
        @ExcelCodeMapping({@ExcelCode(code = "0", label = "禁用"), @ExcelCode(code = "1", label = "启用")})
        private Integer status;
        @ExcelProperty(value = "分类", index = 6, converter = ExcelCodeConverter.class)
        @ExcelCodeMapping(@ExcelCode(code = "001", label = "一般"))
        private String category;
        @ExcelProperty(value = "枚举", index = 7, converter = ExcelCodeConverter.class)
        @ExcelCodeMapping({@ExcelCode(code = "ACTIVE", label = "有效"), @ExcelCode(code = "INACTIVE", label = "无效")})
        private State state;
        @ExcelProperty(value = "启用", index = 8, converter = ExcelCodeConverter.class)
        @ExcelCodeMapping({@ExcelCode(code = "true", label = "是"), @ExcelCode(code = "false", label = "否")})
        private Boolean enabled;

        public LocalDate getDay() { return day; }
        public void setDay(LocalDate day) { this.day = day; }
        public LocalDateTime getTime() { return time; }
        public void setTime(LocalDateTime time) { this.time = time; }
        public Date getLegacy() { return legacy; }
        public void setLegacy(Date legacy) { this.legacy = legacy; }
        public String getIdentifier() { return identifier; }
        public void setIdentifier(String identifier) { this.identifier = identifier; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public State getState() { return state; }
        public void setState(State state) { this.state = state; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public static class NativeDateRow {
        @ExcelProperty(value = "日期", index = 0, converter = ExcelLocalDateConverter.class)
        @DateTimeFormat("yyyy-MM-dd")
        private LocalDate day;
        @ExcelProperty(value = "时间", index = 1, converter = ExcelLocalDateTimeConverter.class)
        private LocalDateTime time;
        @ExcelProperty(value = "旧日期", index = 2, converter = ExcelDateConverter.class)
        @ExcelDateFormat(zoneId = "UTC")
        private Date legacy;

        public LocalDate getDay() { return day; }
        public void setDay(LocalDate day) { this.day = day; }
        public LocalDateTime getTime() { return time; }
        public void setTime(LocalDateTime time) { this.time = time; }
        public Date getLegacy() { return legacy; }
        public void setLegacy(Date legacy) { this.legacy = legacy; }
    }

    public static class StringRow {
        @ExcelProperty(value = "保留", index = 0, converter = ExcelStringConverter.class)
        @ExcelStringFormat(trim = false, emptyToNull = false)
        private String preserved;
        @ExcelProperty(value = "清理", index = 1, converter = ExcelStringConverter.class)
        private String cleaned;
        @ExcelProperty(value = "空文本", index = 2, converter = ExcelStringConverter.class)
        private String blank;

        public String getPreserved() { return preserved; }
        public void setPreserved(String preserved) { this.preserved = preserved; }
        public String getCleaned() { return cleaned; }
        public void setCleaned(String cleaned) { this.cleaned = cleaned; }
        public String getBlank() { return blank; }
        public void setBlank(String blank) { this.blank = blank; }
    }

    public static class NumericCodeRow {
        @ExcelProperty(value = "整数代码", index = 0, converter = ExcelCodeConverter.class)
        @ExcelCodeMapping(@ExcelCode(code = "01", label = "启用"))
        private Integer numeric;
        @ExcelProperty(value = "字符串代码", index = 1, converter = ExcelCodeConverter.class)
        @ExcelCodeMapping(@ExcelCode(code = "00123", label = "人员"))
        private String text;

        public Integer getNumeric() { return numeric; }
        public void setNumeric(Integer numeric) { this.numeric = numeric; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
