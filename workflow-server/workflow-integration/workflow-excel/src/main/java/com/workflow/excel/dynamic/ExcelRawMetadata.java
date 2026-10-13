package com.workflow.excel.dynamic;

import com.alibaba.excel.read.metadata.holder.ReadWorkbookHolder;
import com.alibaba.excel.read.metadata.holder.xls.XlsReadWorkbookHolder;
import com.alibaba.excel.read.metadata.holder.xlsx.XlsxReadWorkbookHolder;
import com.workflow.excel.ExcelReadOptions;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.poi.hssf.eventusermodel.AbortableHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.model.HSSFFormulaParser;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BoolErrRecord;
import org.apache.poi.hssf.record.FormulaRecord;
import org.apache.poi.hssf.record.FormatRecord;
import org.apache.poi.hssf.record.ExtendedFormatRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.RowRecord;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * EasyExcel 会把 XLSX 错误转为文本，也可能丢弃 XLS 的空字符串公式。
 * 此有界元数据预扫仅记录公式/错误坐标，纠正解析器行为；不创建完整 Workbook 或计算公式。
 */
public final class ExcelRawMetadata {
    private static final int MAX_SPECIAL_CELLS = 100_000;
    private static final int MAX_SPECIAL_TEXT = 2_000_000;
    private final TreeMap<Integer, Map<Integer, Override>> rows = new TreeMap<>();
    private int count;
    private long textSize;

    private ExcelRawMetadata() { }

    /** 在实际数据回调前读取特殊类型元数据；资源超限整体拒绝，不截断后继续导入。 */
    public static ExcelRawMetadata read(ReadWorkbookHolder holder, ExcelReadOptions options, long maxRows) {
        ExcelRawMetadata result = new ExcelRawMetadata();
        try {
            if (holder instanceof XlsxReadWorkbookHolder xlsx) result.readXlsx(xlsx, options, maxRows);
            else if (holder instanceof XlsReadWorkbookHolder xls) result.readXls(xls, options, maxRows);
            return result;
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("无法读取 Excel 原始类型元数据", error);
        }
    }

    /** 逐行消费并移除已完成的修正信息，使实际读取时内存占用持续下降。 */
    public Map<Integer, Override> take(int rowIndex) {
        Map<Integer, Override> row = rows.remove(rowIndex);
        return row == null ? Map.of() : row;
    }

    public Integer firstRow() { return rows.isEmpty() ? null : rows.firstKey(); }

    public record Override(ExcelCellType type, String value, Integer formatIndex, String format) { }

    private void put(int row, int column, ExcelCellType type, String value, Integer formatIndex, String format) {
        if (++count > MAX_SPECIAL_CELLS) throw new IllegalArgumentException("Excel 公式/错误单元格超过上限：" + MAX_SPECIAL_CELLS);
        textSize += value == null ? 0 : value.length();
        if (textSize > MAX_SPECIAL_TEXT) throw new IllegalArgumentException("Excel 公式/错误文本总量超过上限");
        rows.computeIfAbsent(row, ignored -> new LinkedHashMap<>()).put(column, new Override(type, value, formatIndex, format));
    }

    private void readXlsx(XlsxReadWorkbookHolder holder, ExcelReadOptions options, long maxRows) throws Exception {
        XSSFReader reader = new XSSFReader(holder.getOpcPackage());
        var sheets = reader.getSheetsData();
        int index = 0;
        while (sheets.hasNext()) {
            try (InputStream input = sheets.next()) {
                if (index++ != options.sheetNo()) continue;
                var parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new DefaultHandler() {
                    private int row = -1;
                    private int column = -1;
                    private int styleIndex;
                    private long dataRows;
                    private ExcelCellType type;
                    private String capture;
                    private final StringBuilder value = new StringBuilder();

                    @java.lang.Override
                    public void startElement(String uri, String name, String qName, Attributes attributes) {
                        if ("row".equals(name)) {
                            String coordinate = attributes.getValue("r");
                            row = coordinate == null ? row + 1 : Integer.parseInt(coordinate) - 1;
                            column = -1;
                            if (row >= options.headRowNumber() && ++dataRows > maxRows) {
                                throw new IllegalArgumentException("Excel 数据行数超过上限：" + maxRows);
                            }
                        } else if ("c".equals(name)) {
                            String reference = attributes.getValue("r");
                            column = reference == null ? column + 1 : new CellReference(reference).getCol();
                            String style = attributes.getValue("s");
                            styleIndex = style == null ? 0 : Integer.parseInt(style);
                            type = "e".equals(attributes.getValue("t")) ? ExcelCellType.ERROR : null;
                            value.setLength(0);
                        } else if ("f".equals(name)) {
                            type = ExcelCellType.FORMULA;
                            capture = "f";
                            value.setLength(0);
                        } else if ("v".equals(name) && type == ExcelCellType.ERROR) capture = "v";
                    }

                    @java.lang.Override
                    public void characters(char[] chars, int start, int length) {
                        if (capture != null) {
                            if (value.length() + length > 32_767) throw new IllegalArgumentException("Excel 单元格内容超过上限");
                            value.append(chars, start, length);
                        }
                    }

                    @java.lang.Override
                    public void endElement(String uri, String name, String qName) {
                        if (name.equals(capture)) capture = null;
                        if ("c".equals(name) && type != null) {
                            var format = holder.dataFormatData(styleIndex);
                            put(row, column, type, value.toString(),
                                    format == null || format.getIndex() == null ? null : Short.toUnsignedInt(format.getIndex()),
                                    format == null ? null : format.getFormat());
                        }
                    }
                });
                parser.parse(new InputSource(input));
                return;
            }
        }
    }

    private void readXls(XlsReadWorkbookHolder holder, ExcelReadOptions options, long maxRows) throws Exception {
        HSSFRequest request = new HSSFRequest();
        request.addListenerForAllRecords(new AbortableHSSFListener() {
            private int sheet = -1;
            private long dataRows;
            private final List<Integer> styleFormats = new ArrayList<>();
            private final Map<Integer, String> customFormats = new HashMap<>();

            @java.lang.Override
            public short abortableProcessRecord(Record record) {
                // 全局样式记录先于数据；仅保留格式索引，供解析器漏掉的空公式补回显示格式。
                if (record instanceof ExtendedFormatRecord style) styleFormats.add(Short.toUnsignedInt(style.getFormatIndex()));
                if (record instanceof FormatRecord format) customFormats.put(format.getIndexCode(), format.getFormatString());
                if (record instanceof BOFRecord bof && bof.getType() == BOFRecord.TYPE_WORKSHEET) {
                    sheet++;
                    if (sheet > options.sheetNo()) return 1;
                }
                if (sheet != options.sheetNo()) return 0;
                if (record instanceof RowRecord row && row.getRowNumber() >= options.headRowNumber() && ++dataRows > maxRows) {
                    throw new IllegalArgumentException("Excel 数据行数超过上限：" + maxRows);
                }
                if (record instanceof FormulaRecord formula) {
                    String value;
                    try {
                        value = HSSFFormulaParser.toFormulaString(holder.getHssfWorkbook(), formula.getParsedExpression());
                    } catch (RuntimeException unsupportedExpression) {
                        // 外部引用等公式不必能完整重建表达式，但绝不能因解析不了而退回普通单元格。
                        value = "[公式]";
                    }
                    int format = styleFormats.get(Short.toUnsignedInt(formula.getXFIndex()));
                    put(formula.getRow(), formula.getColumn(), ExcelCellType.FORMULA, value, format,
                            customFormats.getOrDefault(format, BuiltinFormats.getBuiltinFormat(format)));
                } else if (record instanceof BoolErrRecord error && error.isError()) {
                    int format = styleFormats.get(Short.toUnsignedInt(error.getXFIndex()));
                    put(error.getRow(), error.getColumn(), ExcelCellType.ERROR, FormulaError.forInt(error.getErrorValue()).getString(),
                            format, customFormats.getOrDefault(format, BuiltinFormats.getBuiltinFormat(format)));
                }
                return 0;
            }
        });
        new HSSFEventFactory().abortableProcessWorkbookEvents(request, holder.getPoifsFileSystem());
    }
}
