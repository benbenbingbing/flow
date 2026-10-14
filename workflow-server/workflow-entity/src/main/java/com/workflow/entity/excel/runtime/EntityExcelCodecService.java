package com.workflow.entity.excel.runtime;

import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.dictionary.application.SysDictItemService.DictionaryValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.excel.ExcelReadOptions;
import com.workflow.excel.ExcelUtils;
import com.workflow.excel.dynamic.ExcelCellType;
import com.workflow.excel.dynamic.ExcelRawCell;
import com.workflow.excel.dynamic.ExcelRawRow;
import com.workflow.excel.dynamic.ExcelValueCodec;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 使用固定发布计划进行文件转换和无副作用预检，不保存实体、不执行表单事件。
 * 结构或资源上限错误直接拒绝文件；普通单元格错误有界收集。数据库唯一性和表单规则由保存入口再次检查。
 */
@Service
@RequiredArgsConstructor
public class EntityExcelCodecService {
    private static final int MAX_ERRORS = 10_000;
    // 与业务列表的内置实体状态文案一致；实体中配置的名称可以覆盖这些默认名称。
    private static final Map<String, String> BUILTIN_STATUS_NAMES = Map.of(
            "DRAFT", "草稿", "PENDING", "处理中", "APPROVED", "已完成", "COMPLETED", "已完成",
            "REJECTED", "已驳回", "TERMINATED", "已终止", "WITHDRAWN", "已撤回");
    private static final com.fasterxml.jackson.databind.ObjectMapper DEFAULT_VALUES = new com.fasterxml.jackson.databind.ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    private final SysDictItemService dictionaries;
    private final EntityFieldValidationRuleService fieldRules;
    private final EntityStatusService entityStatuses;
    private final EntityExcelReferenceService references;

    /**
     * 完成文件转换及元数据校验。rows 是截止前的候选行（含失败行），allRows 保留全部非空原始行供结果报告。
     * 分批模式按表头、空行和失败策略选择候选；旧 ATOMIC_ALL 保留按标题映射、跳过空行和全文件校验行为。
     * 静态默认值按已发布实体和表单配置填充；动态编码仍由原保存链生成，预检不提前消耗编码。
     */
    public Parsed parse(EntityExcelRuntimeResolver.Plan plan, InputStream input) {
        var document = plan.scheme().document();
        boolean batchMode = "BATCH".equals(document.importConfig().transactionPolicy());
        var selection = ExcelImportFormColumnPolicy.resolve(plan);
        List<ColumnCodec> columns = compile(plan, true);
        Set<String> ignoredHeaders = new HashSet<>();
        document.columns().stream().filter(column -> column.importEnabled() && selection.ignoredFields().contains(column.fieldCode()))
                .forEach(column -> {
                    ignoredHeaders.add(column.header().strip());
                    if (!batchMode) column.headerAliases().forEach(alias -> ignoredHeaders.add(alias.strip()));
                });
        Map<String, Object> defaults = defaults(plan);
        List<Row> rows = new ArrayList<>();
        List<Row> allRows = new ArrayList<>();
        Map<Integer, String> unimportedReasons = new LinkedHashMap<>();
        boolean[] stopped = {false};
        String[] stopReason = {null};
        int[] lastRowNumber = {document.file().headRowNumber()};
        Errors errors = new Errors();
        Map<Integer, ColumnCodec> mapping = new LinkedHashMap<>();
        Map<ColumnCodec, Integer> indices = new HashMap<>();
        Map<Integer, String> inputHeaders = new LinkedHashMap<>();
        Set<String> mapped = columns.stream().map(column -> column.field.getFieldCode()).collect(Collectors.toSet());
        Map<String, Set<Object>> uniques = new HashMap<>();
        Set<String> invalidRows = new HashSet<>();
        long[] skipped = {0};
        long[] dataCount = {0};
        boolean[] receivedHeader = {false};
        ExcelUtils.readRawRows(input, document.file().sheetName(), new ExcelReadOptions(0, document.file().headRowNumber(), 200, false),
                document.importConfig().maxRows(), head -> {
                    receivedHeader[0] = true;
                    inputHeaders.putAll(rawValues(head));
                    // 旧发布方案保留按标题/别名匹配；新分批方案明确选择严格标题顺序或纯位置读取。
                    try {
                        if (batchMode) mapBatchHeaders(head, columns, ignoredHeaders,
                                document.importConfig().validateHeader(), mapping);
                        else mapHeaders(head, columns, document.file().unknownColumnPolicy(), ignoredHeaders, mapping);
                    } catch (IllegalArgumentException failure) {
                        if (!batchMode) throw failure;
                        stopped[0] = true;
                        stopReason[0] = "表头校验失败：" + message(failure);
                        errors.add(new Error(head.sheetNo(), head.sheetName(), 0, null,
                                null, null, "FILE_HEADER", message(failure)));
                    }
                    mapping.forEach((index, column) -> indices.put(column, index));
                }, batch -> {
                    // 每批按关联列收集输入后统一查询，不能为每个单元格单独访问数据库。
                    if (!stopped[0]) for (ColumnCodec column : columns) if (column.reference != null) {
                        Integer index = indices.get(column);
                        List<String> values = new ArrayList<>();
                        if (index != null) for (ExcelRawRow raw : batch) {
                            try { values.add(ExcelValueCodec.readString(raw.cells().get(index), true, true)); }
                            catch (IllegalArgumentException invalid) { /* 公式等单元格错误由行循环定位。 */ }
                        }
                        column.reference.prepareImport(values);
                    }
                    for (ExcelRawRow raw : batch) {
                        checkColumnLimit(raw);
                        // XLSX 可以根本没有空行 XML；物理行号的跳跃和显式空行具有同样的截止含义。
                        if (batchMode && document.importConfig().stopAtEmptyRow() && !stopped[0]
                                && (raw.isBlank() || raw.rowNumber() > lastRowNumber[0] + 1)) {
                            stopped[0] = true;
                            stopReason[0] = "遇到空行后停止读取";
                        }
                        if (batchMode && raw.rowNumber() > lastRowNumber[0] + 1)
                            skipped[0] += raw.rowNumber() - lastRowNumber[0] - 1;
                        lastRowNumber[0] = raw.rowNumber();
                        if (raw.isBlank()) { skipped[0]++; continue; }
                        dataCount[0]++;
                        if (stopped[0]) {
                            allRows.add(new Row(raw.sheetNo(), raw.sheetName(), raw.rowNumber(), Map.of(), rawValues(raw)));
                            unimportedReasons.put(raw.rowNumber(), stopReason[0]);
                            continue;
                        }
                        Map<String, Object> data = new LinkedHashMap<>(defaults);
                        boolean invalid = false;
                        for (ColumnCodec column : columns) {
                            Integer index = indices.get(column);
                            ExcelRawCell cell = index == null ? null : raw.cells().get(index);
                            try {
                                Object value = column.read(cell);
                                if (blank(value) && defaults.containsKey(column.field.getFieldCode()))
                                    value = defaults.get(column.field.getFieldCode());
                                validate(column, value);
                                if (value != null) data.put(column.field.getFieldCode(), value);
                                // 分批候选还可能被其它字段、表单或保存校验剔除，不能提前占用唯一值。
                                // BATCH 在有效候选预检和每批权威结果中校验；历史原子模式保留全文件检查。
                                if (!batchMode && !blank(value) && (Boolean.TRUE.equals(column.field.getIsUnique())
                                        || flag(column.column.validation(), "uniqueInFile", false))) {
                                    Object key = com.workflow.entity.data.application.EntityUniqueValueService.normalize(value);
                                    if (!uniques.computeIfAbsent(column.field.getFieldCode(), ignored -> new HashSet<>()).add(key)) {
                                        throw new IllegalArgumentException("文件内该字段值重复");
                                    }
                                }
                            } catch (IllegalArgumentException | ArithmeticException failure) {
                                invalid = true;
                                errors.add(new Error(raw.sheetNo(), raw.sheetName(), raw.rowNumber(), index,
                                        column.field.getFieldCode(), column.column.header(),
                                        cell != null && cell.type() == ExcelCellType.FORMULA ? "FORMULA_NOT_SUPPORTED" : "INVALID_VALUE",
                                        message(failure)));
                            }
                        }
                        // 忽略表单外字段或文件未映射列只跳过业务输入；公式和错误单元格仍按文件边界显式报错。
                        for (var entry : raw.cells().entrySet()) {
                            if (mapping.containsKey(entry.getKey())) continue;
                            var cell = entry.getValue();
                            if (cell.type() == ExcelCellType.FORMULA || cell.type() == ExcelCellType.ERROR) {
                                invalid = true;
                                errors.add(new Error(raw.sheetNo(), raw.sheetName(), raw.rowNumber(), entry.getKey(),
                                        null, inputHeaders.get(entry.getKey()),
                                        cell.type() == ExcelCellType.FORMULA ? "FORMULA_NOT_SUPPORTED" : "INVALID_VALUE",
                                        cell.type() == ExcelCellType.FORMULA ? "不支持公式单元格" : "不支持错误单元格"));
                            }
                        }
                        // 未映射的实体必填字段也需检查；有默认值或系统编码的字段留给保存链生成后终检。
                        for (EntityField field : plan.entity().snapshot().getFields()) {
                            if (!mapped.contains(field.getFieldCode()) && Boolean.TRUE.equals(field.getIsRequired())
                                    && !defaults.containsKey(field.getFieldCode()) && !deferredDefault(field)
                                    && !Boolean.TRUE.equals(field.getIsSystem())) {
                                invalid = true;
                                errors.add(new Error(raw.sheetNo(), raw.sheetName(), raw.rowNumber(), null,
                                        field.getFieldCode(), field.getFieldName(), "REQUIRED",
                                        selection.ignoredFields().contains(field.getFieldCode())
                                                ? "实体必填字段被未知列策略忽略，且没有默认值" : "必填字段未配置导入列"));
                            }
                        }
                        if (invalid) invalidRows.add(raw.sheetNo() + ":" + raw.rowNumber());
                        Row row = new Row(raw.sheetNo(), raw.sheetName(), raw.rowNumber(), data, rawValues(raw));
                        rows.add(row);
                        allRows.add(row);
                        if (batchMode && invalid && "STOP".equals(document.importConfig().failurePolicy())) {
                            stopped[0] = true;
                            stopReason[0] = "前序行失败，已停止导入";
                        }
                    }
                });
        if (!receivedHeader[0]) throw new IllegalArgumentException("Excel 缺少表头");
        return new Parsed(rows, errors.items, dataCount[0], skipped[0], rows.size() - invalidRows.size(),
                invalidRows.size(), errors.truncated, inputHeaders, allRows, unimportedReasons);
    }

    /**
     * 复核纯表单预览或正式保存返回的最终行，防止前置处理改变值后绕过方案规则。
     * finalValues 与 parsed.rows 按索引对齐；null 表示该行已由调用方报告失败。只校验规范存储值，
     * 不再次套用 Excel 显示格式、默认值或舍入，不改写输入，也不查询业务记录唯一性。
     * 字典按本次编译读取启用代码；普通错误最多10000条，超出后追加一个 rowNumber=0 的文件级错误。
     */
    public List<Error> validateProcessedRows(EntityExcelRuntimeResolver.Plan plan, Parsed parsed,
            List<Map<String, Object>> finalValues) {
        if (finalValues == null || finalValues.size() != parsed.rows().size())
            throw new IllegalArgumentException("最终校验行必须与原始文件行一一对应");
        List<ColumnCodec> columns = compile(plan, true);
        // 表单前置处理可能改变关联 ID；在最终校验阶段重新批量鉴权。
        for (ColumnCodec column : columns) if (column.reference != null)
            column.reference.prepareIds(finalValues.stream().filter(java.util.Objects::nonNull)
                    .map(values -> processedValue(values, column.field.getFieldCode())).toList());
        List<Error> errors = new ArrayList<>();
        Map<ColumnCodec, Integer> indices = new HashMap<>();
        Map<ColumnCodec, Map<String, List<Row>>> uniqueRows = new LinkedHashMap<>();
        for (ColumnCodec column : columns) {
            // 位置读取不能再用不可信标题回推错误列，否则关闭校验后错误报告会丢失列坐标。
            if ("BATCH".equals(plan.scheme().document().importConfig().transactionPolicy())
                    && !plan.scheme().document().importConfig().validateHeader()) {
                indices.put(column, columns.indexOf(column));
                continue;
            }
            parsed.headers().entrySet().stream().filter(entry -> {
                String header = string(entry.getValue()).strip();
                return column.column.header().strip().equals(header)
                        || column.column.headerAliases().stream().anyMatch(alias -> alias.strip().equals(header));
            }).findFirst().ifPresent(entry -> indices.put(column, entry.getKey()));
        }
        for (int index = 0; index < parsed.rows().size(); index++) {
            Map<String, Object> values = finalValues.get(index);
            if (values == null) continue;
            Row row = parsed.rows().get(index);
            for (ColumnCodec column : columns) {
                try {
                    Object value = processedValue(values, column.field.getFieldCode());
                    // 静态默认值已在解析阶段应用；前置处理清空它时必须重新报必填，不能再次跳过。
                    boolean generatedCode = Boolean.TRUE.equals(column.field.getIsSystem())
                            && "code".equals(column.field.getFieldCode());
                    if (blank(value) && (flag(column.column.validation(), "required", false)
                            || Boolean.TRUE.equals(column.field.getIsRequired()) && !generatedCode))
                        throw new IllegalArgumentException("字段不能为空");
                    Object canonical = column.reference != null ? column.reference.validateIds(value) : canonicalValue(column, value);
                    validate(column, canonical);
                    if (!blank(canonical) && (Boolean.TRUE.equals(column.field.getIsUnique())
                            || flag(column.column.validation(), "uniqueInFile", false))) {
                        String key = com.workflow.entity.data.application.EntityUniqueValueService.normalize(canonical);
                        uniqueRows.computeIfAbsent(column, ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
                    }
                } catch (IllegalArgumentException | ArithmeticException failure) {
                    if (!addProcessedError(errors, processedError(row, column, indices.get(column),
                            "INVALID_VALUE", message(failure)))) return List.copyOf(errors);
                }
            }
        }
        // 在收齐最终值后标记整组重复，避免只有后面的行报错而误导用户保留第一条。
        for (var column : uniqueRows.entrySet()) for (var group : column.getValue().values()) {
            if (group.size() < 2) continue;
            for (Row row : group) if (!addProcessedError(errors, processedError(row, column.getKey(),
                    indices.get(column.getKey()), "DUPLICATE_IN_FILE", "文件内该字段值重复"))) return List.copyOf(errors);
        }
        return List.copyOf(errors);
    }

    /** 兼容标准记录映射器输出的下划线物理列；调用方也可传入字段编码键值。 */
    private static Object processedValue(Map<String, Object> values, String fieldCode) {
        StringBuilder column = new StringBuilder();
        for (char character : fieldCode.toCharArray()) {
            if (Character.isUpperCase(character)) column.append('_').append(Character.toLowerCase(character));
            else column.append(character);
        }
        return values.containsKey(column.toString()) ? values.get(column.toString()) : values.get(fieldCode);
    }

    /**
     * 只接受字段存储类型的 Java/JDBC 或 JSON 标量表示。数值仅作精确校验，日期只接受 ISO 本地值；
     * 不接受金额显示文本、字典名称或配置的 Excel 日期格式，避免把前置处理错误再次“修正”为合法输入。
     */
    private static Object canonicalValue(ColumnCodec column, Object value) {
        if (blank(value)) return value;
        try {
            return switch (column.field.getFieldType()) {
                case STRING, TEXT, SELECT, RADIO -> {
                    if (!(value instanceof String)) throw new IllegalArgumentException();
                    yield value;
                }
                case INTEGER, LONG, DECIMAL -> {
                    if (!(value instanceof Number) && !(value instanceof String)) throw new IllegalArgumentException();
                    BigDecimal number = new BigDecimal(value.toString());
                    if (column.field.getFieldType() == EntityField.FieldType.INTEGER) number.intValueExact();
                    if (column.field.getFieldType() == EntityField.FieldType.LONG) number.longValueExact();
                    // 前置处理不得新增会被数据库或导出格式静默舍入的精度；这里只验证，不执行舍入。
                    number.setScale(column.scale, RoundingMode.UNNECESSARY);
                    if (column.field.getFieldPrecision() != null)
                        number.setScale(column.field.getFieldPrecision(), RoundingMode.UNNECESSARY);
                    yield number;
                }
                case BOOLEAN -> {
                    if (value instanceof Boolean) yield value;
                    if (!(value instanceof Number) && !(value instanceof String)) throw new IllegalArgumentException();
                    yield booleanValue(value.toString());
                }
                case DATE -> {
                    if (value instanceof LocalDate) yield value;
                    if (value instanceof java.sql.Date day) yield day.toLocalDate();
                    if (!(value instanceof String text)) throw new IllegalArgumentException();
                    yield LocalDate.parse(text);
                }
                case DATETIME -> {
                    if (value instanceof LocalDateTime) yield value;
                    if (value instanceof java.sql.Timestamp time) yield time.toLocalDateTime();
                    if (!(value instanceof String text)) throw new IllegalArgumentException();
                    yield LocalDateTime.parse(text.replace(' ', 'T'));
                }
                default -> throw new IllegalArgumentException();
            };
        } catch (RuntimeException invalid) {
            // 不把事件返回的原始内容写入错误信息，错误报告只回显用户上传的数据。
            throw new IllegalArgumentException("最终值不符合字段类型或数值精度要求", invalid);
        }
    }

    private static Error processedError(Row row, ColumnCodec column, Integer index, String code, String message) {
        return new Error(row.sheetNo(), row.sheetName(), row.rowNumber(), index,
                column.field.getFieldCode(), column.column.header(), code, message);
    }

    /** sentinel 不占普通错误额度，调用方合并不同阶段错误时仍可识别文件级截断。 */
    private static boolean addProcessedError(List<Error> errors, Error error) {
        if (errors.size() < MAX_ERRORS) { errors.add(error); return true; }
        errors.add(new Error(0, "", 0, null, null, null, "ERROR_LIMIT", "错误数量超过10000条，后续错误未展开"));
        return false;
    }

    /** 模板只取导入列；导出只取导出列，始终使用方案定义的顺序和标准标题。 */
    public List<List<String>> heads(EntityExcelRuntimeResolver.Plan plan, boolean importMode) {
        return (importMode ? ExcelImportFormColumnPolicy.resolve(plan).importColumns()
                : plan.scheme().document().columns().stream().filter(ExcelSchemeDocument.Column::exportEnabled).toList()).stream()
                .map(column -> List.of(column.header())).toList();
    }

    /** 由同一套转换器生成面向填表者的说明；字典仅列出本次导入接受的启用值。 */
    public List<List<String>> importInstructions(EntityExcelRuntimeResolver.Plan plan) {
        List<List<String>> instructions = new ArrayList<>();
        Set<String> directFields = ExcelImportFormColumnPolicy.resolve(plan).directFields();
        Map<String, Object> defaults = defaults(plan);
        for (ColumnCodec compiled : compile(plan, true)) {
            var column = compiled.column;
            var field = compiled.field;
            var options = column.codec();
            var validation = column.validation();
            boolean required = Boolean.TRUE.equals(field.getIsRequired()) || flag(validation, "required", false);
            String requirement = (column.requiredHeader() ? "必须保留此列；" : "可以省略此列；")
                    + (defaults.containsKey(field.getFieldCode()) || deferredDefault(field) ? "留空时使用系统默认值" : required ? "每行必填" : "允许留空");
            String inputFormat = switch (compiled.type) {
                case "DATE", "DATETIME" -> "日期格式：" + options.getOrDefault("format",
                        "DATE".equals(compiled.type) ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss")
                        + (strings(options.get("readPatterns")).isEmpty() ? "" : "；也接受：" + String.join("、", strings(options.get("readPatterns"))));
                case "AMOUNT", "NUMBER" -> "十进制数，最多" + compiled.scale + "位小数；"
                        + (compiled.rounding == RoundingMode.UNNECESSARY ? "超出精度时报错" : "按方案配置进行舍入")
                        + (flag(options, "allowThousands", true) ? "；支持千分位" : "；不支持千分位");
                case "ENTITY_REFERENCE" -> compiled.reference.instructions();
                case "BOOLEAN" -> "填写 true/false 或 1/0";
                case "DICT" -> "CODE".equals(options.get("importMode")) ? "填写右侧字典代码"
                        : "LABEL".equals(options.get("importMode")) ? "填写右侧字典名称" : "填写右侧字典代码或名称";
                default -> "文本，保留前导零和长编号" + (flag(options, "trim", true) ? "；去除首尾空白" : "；保留空白");
            };
            List<String> rules = new ArrayList<>();
            if (field.getFieldLength() != null && Set.of(EntityField.FieldType.STRING, EntityField.FieldType.TEXT).contains(field.getFieldType()))
                rules.add("最多" + field.getFieldLength() + "个字符");
            if (validation.get("minLength") != null) rules.add("最少" + validation.get("minLength") + "个字符");
            if (validation.get("maxLength") != null) rules.add("最多" + validation.get("maxLength") + "个字符");
            if (validation.get("min") != null) rules.add("不得小于" + validation.get("min"));
            if (validation.get("max") != null) rules.add("不得大于" + validation.get("max"));
            if (validation.get("pattern") != null) rules.add("格式需匹配：" + validation.get("pattern"));
            if (Boolean.TRUE.equals(field.getIsUnique())) rules.add("文件内及已有记录中均不可重复");
            else if (flag(validation, "uniqueInFile", false)) rules.add("文件内不可重复");
            rules.add(directFields.contains(field.getFieldCode()) ? "表单未配置此字段，按方案规则直接插入；实体约束仍生效"
                    : "同时遵循实体和写入表单规则");
            String choices = compiled.dictionary.entrySet().stream().map(entry -> entry.getKey() + " = " + entry.getValue())
                    .collect(Collectors.joining("；"));
            // Excel 单元格最多32767字符；大字典仍能生成模板，并明确说明列表已截断。
            if (choices.length() > 30000) choices = choices.substring(0, 30000) + "…（取值较多，更多选项请查看系统字典）";
            instructions.add(List.of(column.header(), requirement, inputFormat, String.join("；", rules), choices));
        }
        return instructions;
    }

    /**
     * 与表单运行端一致，依次应用实体、表单字段、节点中的静态默认值，写入表单的设置优先。
     * 只读取本次固定发布计划；不执行表达式、接口或编码生成。得到的值随行一起进入预检和正式提交，
     * 因而必填校验无需等待数据库默认值，错误报告仍保留上传者原始单元格。
     */
    private Map<String, Object> defaults(EntityExcelRuntimeResolver.Plan plan) {
        Map<String, Object> configured = new LinkedHashMap<>();
        for (var field : plan.entity().snapshot().getFields()) addDefault(configured, field.getFieldCode(), field.getDefaultValue());
        if (plan.writeForm() != null && plan.writeForm().form() != null) {
            var form = plan.writeForm().form();
            for (var field : ExcelImportFormColumnPolicy.formFieldsForDefaults(form)) addDefault(configured, field.getFieldCode(), field.getDefaultValue());
            for (var node : ExcelImportFormColumnPolicy.fieldNodes(form)) {
                if (node.getPropsDocument() == null || node.getPropsDocument().isBlank()) continue;
                try {
                    Map<String, Object> properties = DEFAULT_VALUES.readValue(node.getPropsDocument(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    String fieldCode = ExcelImportFormColumnPolicy.fieldCode(node);
                    addDefault(configured, fieldCode, properties.get("defaultValue"));
                } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                    throw new IllegalArgumentException("写入表单默认值配置无效", invalid);
                }
            }
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (var field : plan.entity().snapshot().getFields()) {
            if (!configured.containsKey(field.getFieldCode()) || Boolean.TRUE.equals(field.getIsSystem()) && !"name".equals(field.getFieldCode())) continue;
            if (!Set.of(EntityField.FieldType.STRING, EntityField.FieldType.TEXT, EntityField.FieldType.INTEGER, EntityField.FieldType.LONG,
                    EntityField.FieldType.DECIMAL, EntityField.FieldType.BOOLEAN, EntityField.FieldType.DATE, EntityField.FieldType.DATETIME,
                    EntityField.FieldType.SELECT, EntityField.FieldType.RADIO).contains(field.getFieldType())) continue;
            Object value = configured.get(field.getFieldCode());
            if (value instanceof String text) {
                try { value = DEFAULT_VALUES.readValue(text, Object.class); }
                catch (com.fasterxml.jackson.core.JsonProcessingException literal) { value = text; }
            }
            if (value instanceof Map<?, ?> || value instanceof List<?>) throw new IllegalArgumentException("字段默认值必须为单个值：" + field.getFieldName());
            value = coerce(value, field);
            if (!blank(value)) values.put(field.getFieldCode(), value);
        }
        return values;
    }

    private static void addDefault(Map<String, Object> defaults, String field, Object value) {
        if (field != null && !field.isBlank() && value != null && !"".equals(value)) defaults.put(field, value);
    }

    /** 大批量导出应使用 prepareExport 一次编译；此方法适合单条导出。 */
    public List<?> formatRow(EntityExcelRuntimeResolver.Plan plan, EntityDataDTO row) {
        var formatter = prepareExport(plan);
        formatter.prepareBatch(List.of(row));
        return formatter.formatRow(row);
    }

    /** 固定本次导出的字段格式、字典和实体状态名称，之后所有分页共享，避免按行查询或导出中途改名。 */
    public ExportFormatter prepareExport(EntityExcelRuntimeResolver.Plan plan) {
        return new ExportFormatter(compile(plan, false));
    }

    public final class ExportFormatter {
        private final List<ColumnCodec> columns;
        private ExportFormatter(List<ColumnCodec> columns) { this.columns = List.copyOf(columns); }
        public List<List<String>> heads() { return columns.stream().map(column -> List.of(column.column.header())).toList(); }
        /** 分页导出前预取关联记录，缓存只保留本页，避免随文件大小无限增长。 */
        public void prepareBatch(List<EntityDataDTO> rows) {
            for (ColumnCodec column : columns) if (column.reference != null)
                column.reference.prepareIds(rows.stream().map(row -> fieldValue(row, column.field.getFieldCode())).toList());
        }
        public List<?> formatRow(EntityDataDTO row) {
            List<Object> values = new ArrayList<>(columns.size());
            for (ColumnCodec column : columns) values.add(column.write(fieldValue(row, column.field.getFieldCode())));
            return Collections.unmodifiableList(values);
        }
    }

    private List<ColumnCodec> compile(EntityExcelRuntimeResolver.Plan plan, boolean importing) {
        Map<String, EntityField> fields = plan.entity().snapshot().getFields().stream()
                .collect(Collectors.toMap(EntityField::getFieldCode, Function.identity()));
        Map<String, Map<String, String>> dictionariesByCode = new HashMap<>();
        Map<String, String> statusNames = null;
        List<ColumnCodec> codecs = new ArrayList<>();
        List<ExcelSchemeDocument.Column> selected = importing ? ExcelImportFormColumnPolicy.resolve(plan).importColumns()
                : plan.scheme().document().columns().stream().filter(ExcelSchemeDocument.Column::exportEnabled).toList();
        for (var column : selected) {
            EntityField field = fields.get(column.fieldCode());
            if (field == null) throw new IllegalArgumentException("已发布实体字段不存在：" + column.fieldCode());
            if (importing && (Boolean.FALSE.equals(field.getEditable())
                    || Boolean.TRUE.equals(field.getIsSystem()) && !Set.of("name", "code").contains(field.getFieldCode()))) {
                throw new IllegalArgumentException("字段不可导入：" + field.getFieldName());
            }
            if (!importing && Boolean.FALSE.equals(field.getRuntimeReadable())) throw new IllegalArgumentException("字段不可导出：" + field.getFieldName());
            String type = string(column.codec().getOrDefault("type", defaultCodec(field)));
            if (com.workflow.entity.excel.config.EntityExcelReferenceMetadata.isReference(field) != "ENTITY_REFERENCE".equals(type))
                throw new IllegalArgumentException("单选或多选实体必须使用实体关联转换器：" + field.getFieldName());
            if ("ENTITY_STATUS".equals(type)) {
                // 运行时再次守住转换器边界，旧快照或外部配置也不能借名称转换导入系统状态。
                if (importing || column.importEnabled() || !column.exportEnabled())
                    throw new IllegalArgumentException("实体状态转换器仅支持导出");
                if (!"status".equals(field.getFieldCode()) || !Boolean.TRUE.equals(field.getIsSystem()))
                    throw new IllegalArgumentException("实体状态转换器只能绑定系统状态字段 status");
                String exportMode = string(column.codec().getOrDefault("exportMode", "LABEL"));
                if (!Set.of("LABEL", "CODE").contains(exportMode))
                    throw new IllegalArgumentException("实体状态导出模式不合法");
                // 一个文件允许多列引用状态，但名称映射只查一次；导出编码不需要加载名称配置。
                if ("LABEL".equals(exportMode) && statusNames == null)
                    statusNames = statusNames(plan.scheme().entityCode());
            }
            if ("DATE".equals(type) && field.getFieldType() != EntityField.FieldType.DATE
                    || "DATETIME".equals(type) && field.getFieldType() != EntityField.FieldType.DATETIME) {
                throw new IllegalArgumentException("日期转换器必须与实体 DATE/DATETIME 类型一致：" + field.getFieldName());
            }
            // 字典是实体值域约束，不能因选择 STRING 转换器而绕过；导出仍按指定转换器展示。
            Map<String, String> dictionary = ("DICT".equals(type)
                    || importing && field.getDictType() != null && !field.getDictType().isBlank())
                    ? dictionariesByCode.computeIfAbsent(field.getDictType(), code -> dictionary(code, importing)) : Map.of();
            codecs.add(new ColumnCodec(column, field, type, dictionary, importing,
                    "ENTITY_STATUS".equals(type) && statusNames != null ? statusNames : Map.of()));
        }
        return codecs;
    }

    /** 按当前实体配置、内置文案的优先级固定名称映射；未知历史编码由写出时原样保留。 */
    private Map<String, String> statusNames(String entityCode) {
        Map<String, String> names = new LinkedHashMap<>(BUILTIN_STATUS_NAMES);
        entityStatuses.getStatusNameMap(entityCode).forEach((code, name) -> {
            if (code != null && !code.isBlank() && name != null && !name.isBlank()) names.put(code, name);
        });
        return Map.copyOf(names);
    }

    /**
     * 关闭表头校验后直接按模板位置读取，绝不借标题猜测映射。严格模式要求活动列标题和顺序完全一致；
     * IGNORE 可允许方案内已忽略字段仍出现在旧文件中，但它们不占活动列顺序，也不会写入数据。
     */
    private void mapBatchHeaders(ExcelRawRow head, List<ColumnCodec> columns, Set<String> ignoredHeaders,
            boolean validateHeader, Map<Integer, ColumnCodec> mapping) {
        checkColumnLimit(head);
        if (!validateHeader) {
            for (int index = 0; index < columns.size(); index++) mapping.put(index, columns.get(index));
            return;
        }
        int expected = 0;
        int last = head.cells().keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int index = 0; index <= last; index++) {
            ExcelRawCell cell = head.cells().get(index);
            String title = cell == null ? null : ExcelValueCodec.readString(cell, false, false);
            if (title != null && ignoredHeaders.contains(title.strip())) continue;
            if (expected >= columns.size())
                throw new IllegalArgumentException("Excel 表头不一致：第" + (index + 1) + "列不是方案配置列");
            ColumnCodec column = columns.get(expected);
            if (!column.column.header().equals(title))
                throw new IllegalArgumentException("Excel 表头不一致：第" + (index + 1) + "列应为“"
                        + column.column.header() + "”，实际为“" + string(title) + "”");
            mapping.put(index, column);
            expected++;
        }
        if (expected != columns.size())
            throw new IllegalArgumentException("Excel 表头不一致：缺少列“" + columns.get(expected).column.header() + "”");
    }

    private void mapHeaders(ExcelRawRow head, List<ColumnCodec> columns, String unknownPolicy, Set<String> ignoredHeaders,
            Map<Integer, ColumnCodec> mapping) {
        checkColumnLimit(head);
        Map<String, ColumnCodec> titles = new HashMap<>();
        for (ColumnCodec column : columns) {
            titles.put(column.column.header().strip(), column);
            column.column.headerAliases().forEach(alias -> titles.put(alias.strip(), column));
        }
        Set<String> seen = new HashSet<>();
        Set<ColumnCodec> mapped = new HashSet<>();
        for (var entry : head.cells().entrySet()) {
            String title = ExcelValueCodec.readString(entry.getValue(), true, true);
            if (title == null) continue;
            if (!seen.add(title)) throw new IllegalArgumentException("Excel 表头重复：" + title);
            ColumnCodec column = titles.get(title);
            if (column == null) {
                // 方案已识别、因写入表单策略忽略的列，不再按“文件没有映射”误报。
                if (!ignoredHeaders.contains(title) && "REJECT".equals(unknownPolicy))
                    throw new IllegalArgumentException("Excel 存在方案未映射列：" + title);
            } else {
                if (!mapped.add(column)) throw new IllegalArgumentException("Excel 同一字段对应多个表头：" + title);
                mapping.put(entry.getKey(), column);
            }
        }
        for (ColumnCodec column : columns) {
            if (column.column.requiredHeader() && !mapped.contains(column)) throw new IllegalArgumentException("Excel 缺少必需列：" + column.column.header());
        }
    }

    /** 既限制实际列数量，也限制稀疏文件的列坐标，避免错误报告为极远列填充大量空值。 */
    private static void checkColumnLimit(ExcelRawRow row) {
        if (row.cells().size() > 200 || row.cells().keySet().stream().anyMatch(index -> index < 0 || index >= 200))
            throw new IllegalArgumentException("Excel 数据必须位于前200列，文件未导入");
    }

    private void validate(ColumnCodec column, Object value) {
        EntityField field = column.field;
        Map<String, Object> rules = column.column.validation();
        if (blank(value)) {
            if ((Boolean.TRUE.equals(field.getIsRequired()) || flag(rules, "required", false)) && !deferredDefault(field))
                throw new IllegalArgumentException("字段不能为空");
            return;
        }
        if (value instanceof String text && Set.of(EntityField.FieldType.STRING, EntityField.FieldType.TEXT).contains(field.getFieldType())
                && field.getFieldLength() != null && text.codePointCount(0, text.length()) > field.getFieldLength()) {
            throw new IllegalArgumentException("文本超过实体字段长度限制");
        }
        if (!column.dictionary.isEmpty() && !column.dictionary.containsKey(value.toString()))
            throw new IllegalArgumentException("代码不在当前启用字典中");
        fieldRules.validateValue(field, value);
        String text = value.toString();
        int length = text.codePointCount(0, text.length());
        if (rules.get("minLength") != null && length < integer(rules.get("minLength"))) throw new IllegalArgumentException("文本长度小于要求");
        if (rules.get("maxLength") != null && length > integer(rules.get("maxLength"))) throw new IllegalArgumentException("文本长度超过要求");
        if (column.pattern != null && !column.pattern.matcher(text).matches()) throw new IllegalArgumentException("字段格式不符合要求");
        if (rules.get("min") != null && new BigDecimal(text).compareTo(new BigDecimal(string(rules.get("min")))) < 0) throw new IllegalArgumentException("数值小于允许的最小值");
        if (rules.get("max") != null && new BigDecimal(text).compareTo(new BigDecimal(string(rules.get("max")))) > 0) throw new IllegalArgumentException("数值超过允许的最大值");
    }

    private final class ColumnCodec {
        private final ExcelSchemeDocument.Column column;
        private final EntityField field;
        private final String type;
        private final Map<String, String> dictionary;
        private final Map<String, String> statusNames;
        private final ExcelValueCodec.CodeMapping codes;
        private final ExcelValueCodec.DateFormat dates;
        private final Pattern pattern;
        private final ZoneId zone;
        private final int scale;
        private final RoundingMode rounding;
        private final EntityExcelReferenceService.Session reference;

        private ColumnCodec(ExcelSchemeDocument.Column column, EntityField field, String type, Map<String, String> dictionary,
                boolean importing, Map<String, String> statusNames) {
            this.column = column; this.field = field; this.type = type; this.dictionary = dictionary;
            this.statusNames = statusNames;
            this.reference = "ENTITY_REFERENCE".equals(type) ? references.compile(field, column.codec(), importing) : null;
            Map<String, Object> options = column.codec();
            zone = ZoneId.of(string(options.getOrDefault("zoneId", "Asia/Shanghai")));
            dates = Set.of("DATE", "DATETIME").contains(type)
                    ? ExcelValueCodec.dateFormat(string(options.get("format")), strings(options.get("readPatterns")), "DATE".equals(type)) : null;
            // CODE-only 模式只检验代码集合，不要求业务字典的显示名称唯一。
            codes = importing && "DICT".equals(type) && !"CODE".equals(options.get("importMode"))
                    ? new ExcelValueCodec.CodeMapping(dictionary, !"LABEL".equals(options.get("importMode")), true) : null;
            scale = options.get("scale") == null ? Set.of(EntityField.FieldType.INTEGER, EntityField.FieldType.LONG).contains(field.getFieldType())
                    ? 0 : field.getFieldPrecision() == null ? 2 : field.getFieldPrecision() : integer(options.get("scale"));
            rounding = RoundingMode.valueOf(string(options.getOrDefault("roundingMode", "UNNECESSARY")));
            pattern = column.validation().get("pattern") == null ? null : Pattern.compile(string(column.validation().get("pattern")));
        }

        private Object read(ExcelRawCell cell) {
            Map<String, Object> options = column.codec();
            Object result = switch (type) {
                case "ENTITY_REFERENCE" -> reference.read(ExcelValueCodec.readString(cell, true, true));
                case "DATE", "DATETIME" -> {
                    LocalDateTime date = dates.read(cell);
                    yield date == null ? null : "DATE".equals(type) ? date.toLocalDate() : date;
                }
                case "AMOUNT", "NUMBER" -> ExcelValueCodec.readAmount(cell, scale, rounding,
                        flag(options, "allowThousands", true), string(options.get("currencySymbol")));
                case "BOOLEAN" -> booleanValue(ExcelValueCodec.readString(cell, true, true));
                case "DICT" -> {
                    String text = ExcelValueCodec.readString(cell, true, true);
                    if (text == null) yield null;
                    if ("CODE".equals(options.get("importMode"))) {
                        if (!dictionary.containsKey(text)) throw new IllegalArgumentException("代码不在当前启用字典中");
                        yield text;
                    }
                    yield codes.read(cell);
                }
                case "STRING" -> ExcelValueCodec.readString(cell, flag(options, "trim", true), flag(options, "emptyToNull", true));
                default -> throw new IllegalArgumentException("不支持的转换器：" + type);
            };
            return reference != null ? result : coerce(result, field);
        }

        private Object write(Object value) {
            if (value == null) return null;
            Map<String, Object> options = column.codec();
            return switch (type) {
                case "ENTITY_REFERENCE" -> reference.write(value);
                case "DATE", "DATETIME" -> dates.write(toDateTime(value, zone));
                case "AMOUNT", "NUMBER" -> ExcelValueCodec.writeAmount(new BigDecimal(value.toString()), scale, rounding, flag(options, "writeAsText", true));
                case "DICT" -> {
                    String code = string(value);
                    if (!dictionary.containsKey(code)) throw new IllegalArgumentException("导出代码不在字典中：" + field.getFieldName());
                    yield "CODE".equals(options.get("exportMode")) ? code : dictionary.get(code);
                }
                case "ENTITY_STATUS" -> {
                    String code = string(value);
                    // 状态编码不是普通文本：不裁剪、不舍弃未知编码，空字符串也保持为空。
                    yield "CODE".equals(options.get("exportMode")) ? code : statusNames.getOrDefault(code, code);
                }
                case "BOOLEAN" -> booleanValue(string(value));
                default -> ExcelValueCodec.writeString(string(value), flag(options, "trim", true), flag(options, "emptyToNull", true));
            };
        }
    }

    private Map<String, String> dictionary(String dictCode, boolean importing) {
        if (dictCode == null || dictCode.isBlank()) throw new IllegalArgumentException("字段没有绑定字典");
        Map<String, String> result = new LinkedHashMap<>();
        flattenDictionary(dictionaries.listDictionaryValues(dictCode), importing, result);
        if (result.isEmpty()) throw new IllegalArgumentException("字典没有可用选项：" + dictCode);
        return result;
    }

    private void flattenDictionary(List<DictionaryValue> items, boolean importing, Map<String, String> output) {
        if (items == null) return;
        for (DictionaryValue item : items) {
            if (!importing || item.enabled()) {
                if (item.value() == null || item.label() == null || output.putIfAbsent(item.value(), item.label()) != null)
                    throw new IllegalArgumentException("字典值或名称无效，或存在重复代码");
            }
        }
    }

    private static Object coerce(Object value, EntityField field) {
        if (value == null) return null;
        return switch (field.getFieldType()) {
            case INTEGER -> new BigDecimal(value.toString()).intValueExact();
            case LONG -> new BigDecimal(value.toString()).longValueExact();
            case DECIMAL -> new BigDecimal(value.toString());
            case BOOLEAN -> value instanceof Boolean ? value : booleanValue(value.toString());
            case DATE -> value instanceof LocalDate ? value : toDateTime(value).toLocalDate();
            case DATETIME -> toDateTime(value);
            default -> value.toString();
        };
    }

    private static LocalDateTime toDateTime(Object value) {
        return toDateTime(value, ZoneId.of("Asia/Shanghai"));
    }

    /** Excel 本地时间不做时区转换；只有来源实际携带时间点时才按方案 zoneId 展示。 */
    private static LocalDateTime toDateTime(Object value, ZoneId zone) {
        if (value instanceof Instant instant) return LocalDateTime.ofInstant(instant, zone);
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay();
        if (value instanceof Date date) return LocalDateTime.ofInstant(date.toInstant(), zone);
        if (value instanceof OffsetDateTime date) return LocalDateTime.ofInstant(date.toInstant(), zone);
        if (value instanceof ZonedDateTime date) return LocalDateTime.ofInstant(date.toInstant(), zone);
        if (value instanceof LocalDateTime date) return date;
        if (value instanceof LocalDate date) return date.atStartOfDay();
        return ExcelValueCodec.readDateTime(new ExcelRawCell(0, ExcelCellType.STRING, value.toString(), null, null, false), "", List.of());
    }

    private static Boolean booleanValue(String value) {
        if (value == null || value.isBlank()) return null;
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;
        throw new IllegalArgumentException("布尔值必须为 true/false 或 1/0");
    }

    private static Object fieldValue(EntityDataDTO row, String fieldCode) {
        return switch (fieldCode) {
            case "id" -> row.getId(); case "name" -> row.getName(); case "code" -> row.getCode(); case "status" -> row.getStatus();
            case "create_time" -> row.getCreateTime(); case "update_time" -> row.getUpdateTime();
            case "create_by" -> row.getCreateBy(); case "update_by" -> row.getUpdateBy();
            default -> row.getData() == null ? null : row.getData().get(fieldCode);
        };
    }

    private static String defaultCodec(EntityField field) {
        return switch (field.getFieldType()) {
            case REFERENCE, MULTI_REFERENCE -> "ENTITY_REFERENCE";
            case DATE -> "DATE"; case DATETIME -> "DATETIME"; case INTEGER, LONG, DECIMAL -> "NUMBER"; case BOOLEAN -> "BOOLEAN";
            default -> "STRING";
        };
    }
    private static boolean deferredDefault(EntityField field) {
        return field.getDefaultValue() != null && !field.getDefaultValue().isBlank()
                || Boolean.TRUE.equals(field.getIsSystem()) && "code".equals(field.getFieldCode());
    }
    private static boolean blank(Object value) { return value == null || value instanceof String text && text.isBlank() || value instanceof java.util.Collection<?> values && values.isEmpty(); }
    private static String string(Object value) { return value == null ? "" : value.toString(); }
    private static int integer(Object value) { return new BigDecimal(string(value)).intValueExact(); }
    private static boolean flag(Map<String, Object> config, String key, boolean fallback) { return config.get(key) == null ? fallback : Boolean.TRUE.equals(config.get(key)); }
    private static List<String> strings(Object value) { return value instanceof List<?> list ? list.stream().map(EntityExcelCodecService::string).toList() : List.of(); }
    private static String message(Exception failure) { return failure.getMessage() == null ? "单元格格式无效" : failure.getMessage(); }

    /** 错误报告仅展示上传者的原始输入，公式只作为字面文本保存。 */
    private static Map<Integer, String> rawValues(ExcelRawRow row) {
        Map<Integer, String> values = new LinkedHashMap<>();
        row.cells().forEach((index, cell) -> values.put(index, cell.type() == ExcelCellType.FORMULA
                ? "=" + string(cell.value()) : cell.type() == ExcelCellType.ERROR ? string(cell.value())
                : string(ExcelValueCodec.readString(cell, false, false))));
        return values;
    }

    public record Row(int sheetNo, String sheetName, int rowNumber, Map<String, Object> data,
                      Map<Integer, String> rawValues) {
        public Row {
            data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
            rawValues = Collections.unmodifiableMap(new LinkedHashMap<>(rawValues));
        }
    }
    public record Error(int sheetNo, String sheetName, int rowNumber, Integer columnIndex,
                        String fieldCode, String header, String errorCode, String message) {
        @com.fasterxml.jackson.annotation.JsonProperty("columnLetter")
        public String columnLetter() { return columnIndex == null ? "" : CellReference.convertNumToColString(columnIndex); }
    }
    /**
     * allRows 保存包括截止后记录在内的原始非空行；unimportedReasons 的键是 Excel 一基行号。
     * 下游必须只保存 rows 中通过校验的候选，报告则对 allRows 减去实际已提交行，不能丢掉停止后的数据。
     */
    public record Parsed(List<Row> rows, List<Error> errors, long dataRowCount, long skippedRowCount,
                         long validRowCount, long invalidRowCount, boolean errorsTruncated, Map<Integer, String> headers,
                         List<Row> allRows, Map<Integer, String> unimportedReasons) {
        public Parsed {
            rows = List.copyOf(rows); errors = List.copyOf(errors);
            headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            allRows = List.copyOf(allRows);
            unimportedReasons = Collections.unmodifiableMap(new LinkedHashMap<>(unimportedReasons));
        }
        /** 历史原子保存调用没有被截止的行，全部候选也就是全部原始行。 */
        public Parsed(List<Row> rows, List<Error> errors, long dataRowCount, long skippedRowCount,
                      long validRowCount, long invalidRowCount, boolean errorsTruncated, Map<Integer, String> headers) {
            this(rows, errors, dataRowCount, skippedRowCount, validRowCount, invalidRowCount, errorsTruncated,
                    headers, rows, Map.of());
        }
        /** 为一批最终值复核创建同行顺序的视图，避免改变原始报告和其它批次的错误。 */
        public Parsed subset(List<Row> selected) {
            Set<Integer> numbers = selected.stream().map(Row::rowNumber).collect(Collectors.toSet());
            List<Error> selectedErrors = errors.stream().filter(error -> numbers.contains(error.rowNumber())).toList();
            long invalid = selectedErrors.stream().map(Error::rowNumber).distinct().count();
            return new Parsed(selected, selectedErrors, selected.size(), 0, selected.size() - invalid, invalid,
                    errorsTruncated, headers, selected, Map.of());
        }
    }
    private static final class Errors {
        private final List<Error> items = new ArrayList<>();
        private boolean truncated;
        private void add(Error error) {
            if (items.size() < MAX_ERRORS) items.add(error);
            else truncated = true;
        }
    }
}
