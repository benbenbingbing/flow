package com.workflow.migration.history;

import com.workflow.contracts.history.HistoryImportModels.*;
import com.workflow.contracts.history.EntityHistoryImportPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;

/** 标准模板编解码；只读取单元格文本，不计算公式，不把数字格式化为被截断的旧 ID。 */
@Component
@RequiredArgsConstructor
public class HistoryImportWorkbook {
    private final EntityHistoryImportPort entities;
    private static final List<String> INSTANCE = List.of("sourceInstanceId", "entityKey", "sourceRecordId", "name", "initiator", "startedAt", "endedAt", "result", "expectedEvents");
    private static final List<String> EVENT = List.of("sourceEventId", "sourceInstanceId", "sourceNode", "actor", "action", "comment", "handledAt", "sequence");

    public byte[] template(Plan plan, String kind) throws IOException {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            if ("entities".equals(kind)) {
                for (var spec : plan.entities()) {
                    var headers = new ArrayList<>(List.of("sourceRecordId", "groupKey"));
                    headers.addAll(entities.schema(spec.entityCode()).fields().stream().map(Field::code).toList());
                    sheet(book, spec.key(), headers);
                }
            } else if ("history".equals(kind)) { sheet(book, "流程概要", INSTANCE); sheet(book, "审批记录", EVENT); }
            else throw new IllegalArgumentException("未知模板类型");
            book.write(output); return output.toByteArray();
        }
    }

    private void sheet(XSSFWorkbook book, String name, List<String> headers) {
        Sheet sheet = book.createSheet(name); Row row = sheet.createRow(0);
        CellStyle textStyle = book.createCellStyle(); textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
        for (int i = 0; i < headers.size(); i++) { row.createCell(i).setCellValue(headers.get(i)); sheet.setColumnWidth(i, 24 * 256); sheet.setDefaultColumnStyle(i, textStyle); }
        sheet.createFreezePane(0, 1);
    }

    /** 文件总行数有界；错误保留 Sheet/行坐标，空白中间行不能导致后续数据被静默丢弃。 */
    public Input parse(Plan plan, String kind, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("请选择不超过 10MB 的 XLSX 文件");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new IllegalArgumentException("仅支持 XLSX");
        List<EntityRow> rows = new ArrayList<>(); List<InstanceRow> instances = new ArrayList<>(); List<EventRow> events = new ArrayList<>();
        try (var input = file.getInputStream(); var book = new XSSFWorkbook(input)) {
            if (book.getNumberOfSheets() > 20) throw new IllegalArgumentException("最多20个Sheet");
            Set<String> expected = new HashSet<>();
            if ("entities".equals(kind)) {
                for (var spec : plan.entities()) {
                    expected.add(spec.key());
                    var schema = entities.schema(spec.entityCode());
                    Set<String> allowed = new HashSet<>(List.of("sourceRecordId", "groupKey"));
                    schema.fields().forEach(f -> allowed.add(f.code()));
                    for (var row : read(book, spec.key(), allowed, Set.of("sourceRecordId", "groupKey"))) {
                        Map<String, Object> values = new LinkedHashMap<>(row);
                        values.remove("sourceRecordId"); values.remove("groupKey");
                        rows.add(new EntityRow(spec.key(), row.get("sourceRecordId"), row.get("groupKey"), values));
                    }
                }
            } else if ("history".equals(kind)) {
                expected.addAll(List.of("流程概要", "审批记录"));
                for (var row : read(book, "流程概要", new HashSet<>(INSTANCE), new HashSet<>(INSTANCE)))
                    instances.add(new InstanceRow(row.get("sourceInstanceId"), row.get("entityKey"), row.get("sourceRecordId"), row.get("name"), row.get("initiator"), row.get("startedAt"), row.get("endedAt"), row.get("result"), integer(row.get("expectedEvents"))));
                for (var row : read(book, "审批记录", new HashSet<>(EVENT), new HashSet<>(EVENT)))
                    events.add(new EventRow(row.get("sourceEventId"), row.get("sourceInstanceId"), row.get("sourceNode"), row.get("actor"), row.get("action"), row.get("comment"), row.get("handledAt"), integer(row.get("sequence"))));
            } else throw new IllegalArgumentException("未知导入类型");
            for (Sheet sheet : book) if (!expected.contains(sheet.getSheetName())) throw new IllegalArgumentException("未知Sheet: " + sheet.getSheetName());
        }
        if (rows.size() + instances.size() + events.size() > HistoryImportPlanner.MAX_ROWS) throw new IllegalArgumentException("文件最多2000行数据");
        return new Input(rows, instances, events);
    }

    private int integer(String value) {
        try { return Integer.parseInt(value); } catch (Exception e) { throw new IllegalArgumentException("记录数和审批顺序必须是整数"); }
    }

    private List<Map<String, String>> read(Workbook book, String name, Set<String> allowed, Set<String> required) {
        Sheet sheet = book.getSheet(name);
        if (sheet == null || sheet.getRow(0) == null) throw new IllegalArgumentException("缺少Sheet或表头: " + name);
        if (sheet.getLastRowNum() > HistoryImportPlanner.MAX_ROWS) throw new IllegalArgumentException(name + " 超过2000行");
        List<String> headers = new ArrayList<>();
        for (Cell cell : sheet.getRow(0)) {
            String header = value(cell);
            if (!allowed.contains(header) || headers.contains(header)) throw new IllegalArgumentException(name + " 未知或重复表头: " + header);
            if (cell.getColumnIndex() != headers.size()) throw new IllegalArgumentException(name + " 表头不能有空列");
            headers.add(header);
        }
        if (!headers.containsAll(required)) throw new IllegalArgumentException(name + " 缺少必要表头");
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i); if (row == null) continue;
            Map<String, String> values = new LinkedHashMap<>(); boolean any = false;
            try {
                for (int j = 0; j < headers.size(); j++) { String value = value(row.getCell(j)); values.put(headers.get(j), value); any |= !value.isBlank(); }
                for (int j = headers.size(); j < row.getLastCellNum(); j++) if (!value(row.getCell(j)).isBlank()) throw new IllegalArgumentException("存在无表头数据列");
            } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(name + " 第" + (i + 1) + "行: " + invalid.getMessage()); }
            if (any) result.add(values);
        }
        return result;
    }

    private String value(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) throw new IllegalArgumentException("请将公式转换为文本值后导入");
        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) yield cell.getLocalDateTimeCellValue().toString();
                yield new java.math.BigDecimal(org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue())).toPlainString();
            }
            default -> "";
        };
        if (value.length() > 10000) throw new IllegalArgumentException("单元格超过10000字");
        return value;
    }
}
