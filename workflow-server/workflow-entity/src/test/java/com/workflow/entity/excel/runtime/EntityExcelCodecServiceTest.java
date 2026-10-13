package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.dictionary.application.SysDictItemService.DictionaryValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.excel.ExcelUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityExcelCodecServiceTest {
    private final SysDictItemService dictionaries = mock(SysDictItemService.class);
    private final EntityStatusService statuses = mock(EntityStatusService.class);
    private final EntityExcelCodecService service = new EntityExcelCodecService(dictionaries,
            new EntityFieldValidationRuleService(new ObjectMapper()), statuses);

    @Test
    void exportsEntityStatusNamesWithEntityOverridesBuiltinsAndUnknownCodeFallback() {
        var names = new java.util.HashMap<>(Map.of("DRAFT", "待填写", "FINANCE_REVIEW", "财务复核中"));
        when(statuses.getStatusNameMap("employee")).thenReturn(names);
        var status = field("status", EntityField.FieldType.STRING, false); status.setIsSystem(true);
        var formatter = service.prepareExport(plan(List.of(status), List.of(statusColumn("status", "LABEL"),
                new ExcelSchemeDocument.Column("status_copy", "状态副本", List.of(), "status", false, true,
                        false, 20, Map.of("type", "ENTITY_STATUS"), Map.of()))));
        // 先固定映射再格式化分页，导出期间配置变化不应使同一文件前后名称不同。
        names.put("DRAFT", "中途更名");
        Map<String, String> expected = Map.of("DRAFT", "待填写", "FINANCE_REVIEW", "财务复核中",
                "PENDING", "处理中", "APPROVED", "已完成", "COMPLETED", "已完成", "REJECTED", "已驳回",
                "TERMINATED", "已终止", "WITHDRAWN", "已撤回", "LEGACY_STATUS", "LEGACY_STATUS");
        expected.forEach((code, label) -> {
            var row = new EntityDataDTO(); row.setStatus(code);
            assertEquals(List.of(label, label), formatter.formatRow(row));
        });
        var empty = new EntityDataDTO();
        assertEquals(java.util.Arrays.asList(null, null), formatter.formatRow(empty));
        empty.setStatus(""); assertEquals(List.of("", ""), formatter.formatRow(empty));
        verify(statuses, times(1)).getStatusNameMap("employee");
        verifyNoInteractions(dictionaries);
    }

    @Test
    void statusCodeExportDoesNotReadNamesAndKeepsOriginalCode() {
        var status = field("status", EntityField.FieldType.STRING, false); status.setIsSystem(true);
        var formatter = service.prepareExport(plan(List.of(status), List.of(statusColumn("status", "CODE"))));
        var row = new EntityDataDTO(); row.setStatus("DRAFT");
        assertEquals(List.of("DRAFT"), formatter.formatRow(row));
        row.setStatus(" LEGACY "); assertEquals(List.of(" LEGACY "), formatter.formatRow(row));
        verifyNoInteractions(statuses, dictionaries);
    }

    @Test
    void statusNamesAreWrittenAsTextInTheExportedWorkbook() throws Exception {
        when(statuses.getStatusNameMap("employee")).thenReturn(Map.of("FINANCE_REVIEW", "财务复核中"));
        var status = field("status", EntityField.FieldType.STRING, false); status.setIsSystem(true);
        var formatter = service.prepareExport(plan(List.of(status), List.of(statusColumn("status", "LABEL"))));
        var rows = new java.util.ArrayList<List<?>>();
        for (String code : List.of("FINANCE_REVIEW", "DRAFT", "LEGACY_STATUS")) {
            var row = new EntityDataDTO(); row.setStatus(code); rows.add(formatter.formatRow(row));
        }
        var output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, formatter.heads(), rows, "数据");
        try (var workbook = new XSSFWorkbook(input(output))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("状态", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("财务复核中", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("草稿", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("LEGACY_STATUS", sheet.getRow(3).getCell(0).getStringCellValue());
        }
        verify(statuses, times(1)).getStatusNameMap("employee");
    }

    @Test
    void runtimeRejectsStatusCodecOnNonSystemFieldsProcessStatusAndInvalidMode() {
        for (String code : List.of("status", "processStatus", "businessStatus")) {
            var field = field(code, EntityField.FieldType.STRING, false);
            field.setIsSystem(!"status".equals(code));
            var invalid = plan(List.of(field), List.of(statusColumn(code, "LABEL")));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.prepareExport(invalid))
                    .getMessage().contains("系统状态字段 status"));
        }
        var status = field("status", EntityField.FieldType.STRING, false); status.setIsSystem(true);
        var invalid = plan(List.of(status), List.of(statusColumn("status", "LABEL_OR_CODE")));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.prepareExport(invalid))
                .getMessage().contains("实体状态导出模式不合法"));
        var importing = plan(List.of(field("name", EntityField.FieldType.STRING, false)),
                List.of(column("name", "名称", Map.of("type", "ENTITY_STATUS"), Map.of())));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.parse(importing, new ByteArrayInputStream(new byte[0])))
                .getMessage().contains("仅支持导出"));
        verifyNoInteractions(statuses, dictionaries);
    }

    private static ExcelSchemeDocument.Column statusColumn(String field, String mode) {
        return new ExcelSchemeDocument.Column(field, "状态", List.of(), field, false, true,
                false, 20, Map.of("type", "ENTITY_STATUS", "exportMode", mode), Map.of());
    }

    @Test
    void convertsAliasesReorderedColumnsDatesAndDictionaryUsingPublishedFields() throws Exception {
        var name = field("name", EntityField.FieldType.STRING, true);
        var amount = field("amount", EntityField.FieldType.DECIMAL, false);
        var day = field("day", EntityField.FieldType.DATE, false);
        var status = field("statusValue", EntityField.FieldType.SELECT, false);
        status.setDictType("employment");
        when(dictionaries.listDictionaryValues("employment")).thenReturn(List.of(item("01", "在职")));
        var plan = plan(List.of(name, amount, day, status), List.of(
                column("name", "姓名", Map.of("type", "STRING"), Map.of()),
                column("amount", "金额", Map.of("type", "AMOUNT", "scale", 2), Map.of("min", "0")),
                column("day", "日期", Map.of("type", "DATE"), Map.of()),
                column("statusValue", "状态", Map.of("type", "DICT"), Map.of())));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("说明").createRow(0).createCell(0).setCellValue("此页不参与导入");
            var sheet = workbook.createSheet("数据");
            var header = sheet.createRow(0);
            List<String> titles = List.of("状态", "日期", "姓名旧标题", "金额");
            for (int i = 0; i < titles.size(); i++) header.createCell(i).setCellValue(titles.get(i));
            var row = sheet.createRow(2);
            List<String> values = List.of("在职", "2024/02/29", " 小王 ", "1,234.50");
            for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
            workbook.write(output);
        }
        var parsed = service.parse(plan, input(output));
        assertTrue(parsed.errors().isEmpty());
        assertEquals(1, parsed.rows().size());
        assertEquals(3, parsed.rows().get(0).rowNumber());
        assertEquals(1, parsed.rows().get(0).sheetNo());
        assertEquals("姓名旧标题", parsed.headers().get(2));
        assertEquals(Map.of("name", "小王", "amount", new BigDecimal("1234.50"), "day", LocalDate.of(2024, 2, 29), "statusValue", "01"), parsed.rows().get(0).data());
        verify(dictionaries, times(1)).listDictionaryValues("employment");
    }

    @Test
    void collectsMultipleCellsAndKeepsErrorRowsRawValuesAndCoordinates() throws Exception {
        var name = field("name", EntityField.FieldType.STRING, true);
        name.setValidateRules("{\"minLength\":2}");
        var amount = field("amount", EntityField.FieldType.DECIMAL, false);
        var plan = plan(List.of(name, amount), List.of(column("name", "姓名", Map.of("type", "STRING"), Map.of()),
                column("amount", "金额", Map.of("type", "AMOUNT"), Map.of())));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("数据");
            sheet.createRow(0).createCell(0).setCellValue("姓名");
            sheet.getRow(0).createCell(1).setCellValue("金额");
            sheet.createRow(1).createCell(0).setCellValue(" ");
            sheet.getRow(1).createCell(1).setCellValue("12,34");
            sheet.createRow(3).createCell(0).setCellValue("短");
            sheet.getRow(3).createCell(1).setCellFormula("1+1");
            workbook.write(output);
        }
        var parsed = service.parse(plan, input(output));
        assertEquals(2, parsed.rows().size());
        assertEquals(4, parsed.errors().size());
        assertEquals(2, parsed.invalidRowCount());
        assertEquals(0, parsed.validRowCount());
        assertEquals(" ", parsed.rows().get(0).rawValues().get(0));
        assertEquals("=1+1", parsed.rows().get(1).rawValues().get(1));
        assertEquals("FORMULA_NOT_SUPPORTED", parsed.errors().get(3).errorCode());
        assertEquals("B", parsed.errors().get(3).columnLetter());
        assertEquals(4, parsed.errors().get(3).rowNumber());
    }

    @Test
    void validatesFileUniquenessAndAppliesPublishedStaticDefaults() {
        var name = field("name", EntityField.FieldType.STRING, true);
        name.setIsUnique(true);
        var department = field("department", EntityField.FieldType.STRING, true);
        department.setDefaultValue("finance");
        var plan = plan(List.of(name, department), List.of(column("name", "姓名", Map.of("type", "STRING"), Map.of())));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("姓名")), List.of(List.of("张三"), List.of("张三")), "数据");
        var parsed = service.parse(plan, input(output));
        assertEquals(1, parsed.errors().size());
        assertEquals(3, parsed.errors().get(0).rowNumber());
        assertTrue(parsed.errors().get(0).message().contains("重复"));
        assertEquals("finance", parsed.rows().get(0).data().get("department"));
    }

    @Test
    void publishedWriteFormDefaultsOverrideEntityDefaultsWithoutChangingUploadedValues() {
        var name = field("name", EntityField.FieldType.STRING, true);
        var department = field("department", EntityField.FieldType.STRING, true); department.setDefaultValue("entity-dept");
        var amount = field("amount", EntityField.FieldType.DECIMAL, true); amount.setDefaultValue("1.20");
        var base = plan(List.of(name, department, amount), List.of(column("name", "姓名", Map.of("type", "STRING"), Map.of()),
                column("amount", "金额", Map.of("type", "AMOUNT"), Map.of())));
        var form = new com.workflow.entity.form.infrastructure.persistence.record.EntityForm(); form.setId("form1");
        var field = new com.workflow.entity.form.infrastructure.persistence.record.EntityFormField();
        field.setFieldCode("department"); field.setDefaultValue("form-dept"); form.setFields(List.of(field));
        var node = new com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode();
        node.setNodeType("FIELD"); node.setBindingType("ENTITY_FIELD"); node.setBindingRef("department");
        node.setPropsDocument("{\"defaultValue\":\"node-dept\"}"); form.setNodes(List.of(node));
        var plan = new EntityExcelRuntimeResolver.Plan(null, base.scheme(), null, base.entity(),
                new com.workflow.entity.form.application.model.ResolvedEntityFormRelease(form, "release", 1));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("姓名"), List.of("金额")), List.of(List.of("张三", ""), List.of("李四", "2.30")), "数据");
        var parsed = service.parse(plan, input(output));
        assertTrue(parsed.errors().isEmpty());
        assertEquals("node-dept", parsed.rows().get(0).data().get("department"));
        assertEquals(new BigDecimal("1.20"), parsed.rows().get(0).data().get("amount"));
        assertEquals(new BigDecimal("2.30"), parsed.rows().get(1).data().get("amount"));
        assertFalse(parsed.rows().get(0).rawValues().containsValue("node-dept"));
    }

    @Test
    void rejectsUnknownDuplicateHeadersAndMissingRequiredColumn() {
        var plan = plan(List.of(field("name", EntityField.FieldType.STRING, true)),
                List.of(column("name", "姓名", Map.of("type", "STRING"), Map.of())));
        for (List<List<String>> headers : List.of(List.of(List.of("未知")), List.of(List.of("姓名"), List.of("姓名旧标题")))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ExcelUtils.writeRows(output, headers, List.of(List.of("张三")), "数据");
            assertThrows(RuntimeException.class, () -> service.parse(plan, input(output)));
        }
    }

    @Test
    void directImportsFormUnknownFieldAndStillChecksExcelRules() {
        var plan = withFormPolicy(plan(List.of(field("name", EntityField.FieldType.STRING, true), field("amount", EntityField.FieldType.DECIMAL, false)),
                List.of(column("name", "姓名", Map.of(), Map.of()),
                        column("amount", "金额", Map.of("type", "AMOUNT"), Map.of("min", "0")))), "DIRECT", "name");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("姓名"), List.of("金额")), List.of(List.of("张三", "5.00"), List.of("李四", "-1")), "数据");
        var parsed = service.parse(plan, input(output));
        assertEquals(new BigDecimal("5.00"), parsed.rows().get(0).data().get("amount"));
        assertEquals(1, parsed.errors().size());
        assertEquals("amount", parsed.errors().get(0).fieldCode());
        assertTrue(service.importInstructions(plan).get(1).get(3).contains("直接插入"));
        assertEquals(java.util.Set.of("amount"), ExcelImportFormColumnPolicy.resolve(plan).directFields());
        assertFalse(service.validateProcessedRows(plan, parsed, List.of(Map.of("name", "张三", "amount", new BigDecimal("-3.00")),
                Map.of("name", "李四", "amount", new BigDecimal("1.00")))).isEmpty());
    }

    @Test
    void ignoreSkipsMappedInputConversionHeadersAndRevalidationButRetainsEntityDefaults() {
        var amount = field("amount", EntityField.FieldType.DECIMAL, true); amount.setDefaultValue("3.00");
        var plan = withFormPolicy(plan(List.of(field("name", EntityField.FieldType.STRING, true), amount),
                List.of(column("name", "姓名", Map.of(), Map.of()),
                        column("amount", "金额", Map.of("type", "AMOUNT"), Map.of("required", true, "min", "5")))), "IGNORE", "name");
        // 已配置标题和别名均被识别；文件的 REJECT 只拒绝真正没有映射的额外表头。
        for (String header : List.of("金额", "金额旧标题")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ExcelUtils.writeRows(output, List.of(List.of("姓名"), List.of(header)), List.of(List.of("张三", "不是金额")), "数据");
            var parsed = service.parse(plan, input(output));
            assertTrue(parsed.errors().isEmpty());
            assertEquals(new BigDecimal("3.00"), parsed.rows().get(0).data().get("amount"));
            assertTrue(service.validateProcessedRows(plan, parsed, List.of(parsed.rows().get(0).data())).isEmpty());
        }
        ByteArrayOutputStream noIgnoredColumn = new ByteArrayOutputStream();
        ExcelUtils.writeRows(noIgnoredColumn, List.of(List.of("姓名")), List.of(List.of("李四")), "数据");
        assertTrue(service.parse(plan, input(noIgnoredColumn)).errors().isEmpty());
        assertEquals(List.of(List.of("姓名")), service.heads(plan, true));
        assertEquals(1, service.importInstructions(plan).size());
        assertEquals(List.of(List.of("姓名"), List.of("金额")), service.heads(plan, false));
        ByteArrayOutputStream unmapped = new ByteArrayOutputStream();
        ExcelUtils.writeRows(unmapped, List.of(List.of("姓名"), List.of("未映射标题")), List.of(List.of("张三", "x")), "数据");
        assertThrows(RuntimeException.class, () -> service.parse(plan, input(unmapped)));
    }

    @Test
    void ignoredOptionalValueIsNotSavedAndIgnoredRequiredEntityStillFailsWithoutDefault() {
        var extra = field("extra", EntityField.FieldType.STRING, false);
        var plan = withFormPolicy(plan(List.of(field("name", EntityField.FieldType.STRING, true), extra),
                List.of(column("name", "姓名", Map.of(), Map.of()), column("extra", "额外值", Map.of(), Map.of("required", true)))), "IGNORE", "name");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("姓名"), List.of("额外值")), List.of(List.of("张三", "忽略此值")), "数据");
        var parsed = service.parse(plan, input(output));
        assertTrue(parsed.errors().isEmpty());
        assertFalse(parsed.rows().get(0).data().containsKey("extra"));
        extra.setIsRequired(true);
        assertEquals("REQUIRED", service.parse(plan, input(output)).errors().get(0).errorCode());
    }

    @Test
    void rejectReportsFormUnknownMappingsBeforeReadingFileAndGeneratingTemplate() {
        var plan = withFormPolicy(plan(List.of(field("name", EntityField.FieldType.STRING, true), field("amount", EntityField.FieldType.DECIMAL, false)),
                List.of(column("name", "姓名", Map.of(), Map.of()), column("amount", "金额", Map.of("type", "AMOUNT"), Map.of()))), "REJECT", "name");
        var error = assertThrows(IllegalArgumentException.class, () -> service.parse(plan, new ByteArrayInputStream(new byte[0])));
        assertTrue(error.getMessage().contains("写入表单未配置"));
        assertTrue(error.getMessage().contains("金额（amount）"));
        assertThrows(IllegalArgumentException.class, () -> service.heads(plan, true));
        assertEquals(2, service.heads(plan, false).size());
    }

    @Test
    void ignoreCannotCreateDefaultOnlyRowsWhenAllMappedFieldsAreOutsideForm() {
        var plan = withFormPolicy(plan(List.of(field("name", EntityField.FieldType.STRING, false)),
                List.of(column("name", "姓名", Map.of(), Map.of()))), "IGNORE");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.parse(plan, new ByteArrayInputStream(new byte[0]))).getMessage().contains("没有可导入字段"));
        assertThrows(IllegalArgumentException.class, () -> service.heads(plan, true));
    }

    @Test
    void preparesExportDictionaryOnceAndPreservesAmountPrecisionAndLiteralText() {
        var name = field("name", EntityField.FieldType.STRING, false);
        var amount = field("amount", EntityField.FieldType.DECIMAL, false);
        var status = field("statusValue", EntityField.FieldType.SELECT, false); status.setDictType("employment");
        when(dictionaries.listDictionaryValues("employment")).thenReturn(List.of(item("01", "在职")));
        var plan = plan(List.of(name, amount, status), List.of(column("name", "姓名", Map.of("type", "STRING"), Map.of()),
                column("amount", "金额", Map.of("type", "AMOUNT"), Map.of()), column("statusValue", "状态", Map.of("type", "DICT"), Map.of())));
        var formatter = service.prepareExport(plan);
        EntityDataDTO record = new EntityDataDTO(); record.setName("=1+1");
        record.setData(Map.of("amount", new BigDecimal("9999999999999999.99"), "statusValue", "01"));
        assertEquals(List.of("=1+1", "9999999999999999.99", "在职"), formatter.formatRow(record));
        assertEquals(List.of("=1+1", "9999999999999999.99", "在职"), formatter.formatRow(record));
        assertEquals(List.of(List.of("姓名"), List.of("金额"), List.of("状态")), formatter.heads());
        verify(dictionaries, times(1)).listDictionaryValues("employment");
    }

    @Test
    void usesConfiguredZoneOnlyForInstantValuesAndRejectsDateTimeTruncation() {
        var time = field("time", EntityField.FieldType.DATETIME, false);
        var plan = plan(List.of(time), List.of(column("time", "时间", Map.of("type", "DATETIME", "zoneId", "UTC"), Map.of())));
        var formatter = service.prepareExport(plan);
        EntityDataDTO record = new EntityDataDTO();
        record.setData(Map.of("time", java.time.OffsetDateTime.parse("2026-10-12T08:00:00+08:00")));
        assertEquals(List.of("2026-10-12 00:00:00"), formatter.formatRow(record));
        record.setData(Map.of("time", java.time.LocalDateTime.of(2026, 10, 12, 8, 0)));
        assertEquals(List.of("2026-10-12 08:00:00"), formatter.formatRow(record));
        var invalid = plan(List.of(time), List.of(column("time", "时间", Map.of("type", "DATE"), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> service.prepareExport(invalid));
    }

    @Test
    void templateInstructionsExplainDictionaryInputWithoutExposingConfigurationJson() {
        var status = field("status", EntityField.FieldType.SELECT, true); status.setDictType("employment");
        var disabled = new DictionaryValue("00", "停用选项", false);
        when(dictionaries.listDictionaryValues("employment")).thenReturn(List.of(item("01", "在职"), disabled));
        var plan = plan(List.of(status), List.of(column("status", "状态", Map.of("type", "DICT", "importMode", "LABEL"), Map.of())));
        var explanation = service.importInstructions(plan);
        assertEquals(1, explanation.size());
        assertTrue(explanation.get(0).get(1).contains("每行必填"));
        assertEquals("填写右侧字典名称", explanation.get(0).get(2));
        assertEquals("01 = 在职", explanation.get(0).get(4));
        assertFalse(explanation.toString().contains("importMode"));
    }

    @Test
    void rejectsFormulaInIgnoredColumnAndRejectsSparseColumnBeyondLimit() throws Exception {
        var base = plan(List.of(field("name", EntityField.FieldType.STRING, true)),
                List.of(column("name", "姓名", Map.of("type", "STRING"), Map.of())));
        var document = base.scheme().document();
        var ignored = new ExcelSchemeDocument(document.schemaVersion(), document.schemeCode(), document.name(), document.description(),
                new ExcelSchemeDocument.FileConfig("数据", 1, "IGNORE"), document.importConfig(), document.exportConfig(), document.columns());
        var plan = new EntityExcelRuntimeResolver.Plan(null, new ExcelResolvedScheme("entity1", "employee", "basic", ignored, "hash", "", "form1"),
                null, base.entity(), null);
        for (int columnIndex : List.of(1, 200)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                var sheet = workbook.createSheet("数据");
                sheet.createRow(0).createCell(0).setCellValue("姓名");
                sheet.getRow(0).createCell(columnIndex).setCellValue("忽略列");
                sheet.createRow(1).createCell(0).setCellValue("张三");
                sheet.getRow(1).createCell(columnIndex).setCellFormula("1+1");
                workbook.write(output);
            }
            if (columnIndex == 200) assertThrows(RuntimeException.class, () -> service.parse(plan, input(output)));
            else {
                var parsed = service.parse(plan, input(output));
                assertEquals(1, parsed.invalidRowCount());
                assertEquals("FORMULA_NOT_SUPPORTED", parsed.errors().get(0).errorCode());
                assertEquals("忽略列", parsed.errors().get(0).header());
                assertEquals("=1+1", parsed.rows().get(0).rawValues().get(1));
            }
        }
    }

    @Test
    void processedValuesMarkEveryDuplicateAfterFormChangesAndKeepOriginalAliasCoordinates() {
        var name = field("name", EntityField.FieldType.STRING, true);
        var plan = plan(List.of(name), List.of(column("name", "姓名", Map.of("type", "STRING"), Map.of("uniqueInFile", true))));
        var parsed = processedRows(3, Map.of(4, " 姓名旧标题 "));
        Map<String, Object> first = Map.of("name", "Shared");
        Map<String, Object> third = Map.of("name", " shared ");
        var errors = service.validateProcessedRows(plan, parsed, java.util.Arrays.asList(first, null, third));
        assertEquals(2, errors.size());
        assertEquals(List.of(2, 4), errors.stream().map(EntityExcelCodecService.Error::rowNumber).toList());
        assertTrue(errors.stream().allMatch(error -> "DUPLICATE_IN_FILE".equals(error.errorCode())));
        assertTrue(errors.stream().allMatch(error -> error.columnIndex() == 4 && "E".equals(error.columnLetter())));
        assertEquals("Shared", first.get("name"));
        assertEquals(" shared ", third.get("name"));
        assertEquals("原始输入1", parsed.rows().get(0).rawValues().get(4));
        verifyNoInteractions(dictionaries);
    }

    @Test
    void processedValuesRecheckRequiredStaticDefaultAndEverySchemeConstraint() {
        var name = field("name", EntityField.FieldType.STRING, true); name.setDefaultValue("default-name");
        var amount = field("amount", EntityField.FieldType.DECIMAL, false);
        var plan = plan(List.of(name, amount), List.of(
                column("name", "姓名", Map.of("type", "STRING"), Map.of("minLength", 2, "maxLength", 3, "pattern", "[A-Z]+")),
                column("amount", "金额", Map.of("type", "AMOUNT", "scale", 2, "roundingMode", "HALF_UP"), Map.of("min", 1, "max", 10))));
        var values = List.<Map<String, Object>>of(
                Map.of("name", "", "amount", new BigDecimal("0.99")),
                Map.of("name", "A", "amount", new BigDecimal("10.01")),
                Map.of("name", "LONG", "amount", new BigDecimal("1.001")),
                Map.of("name", "bad", "amount", new BigDecimal("1.20")));
        var errors = service.validateProcessedRows(plan, processedRows(4, Map.of(0, "姓名", 1, "金额")), values);
        assertEquals(7, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.rowNumber() == 2 && "字段不能为空".equals(error.message())));
        assertEquals(new BigDecimal("1.001"), values.get(2).get("amount"));
        assertTrue(errors.stream().allMatch(error -> "INVALID_VALUE".equals(error.errorCode())));
    }

    @Test
    void processedDictionaryAcceptsCanonicalCodesRegardlessOfLabelImportModeAndRejectsDisabledCodes() {
        var status = field("status_value", EntityField.FieldType.SELECT, false); status.setDictType("employment");
        when(dictionaries.listDictionaryValues("employment")).thenReturn(List.of(item("01", "在职"), new DictionaryValue("00", "停用", false)));
        var plan = plan(List.of(status), List.of(column("status_value", "状态", Map.of("type", "DICT", "importMode", "LABEL"), Map.of())));
        var errors = service.validateProcessedRows(plan, processedRows(3, Map.of(0, "状态")), List.of(
                Map.of("status_value", "01"), Map.of("status_value", "在职"), Map.of("status_value", "00")));
        assertEquals(List.of(3, 4), errors.stream().map(EntityExcelCodecService.Error::rowNumber).toList());
        assertTrue(errors.stream().allMatch(error -> error.message().contains("启用字典")));
        verify(dictionaries, times(1)).listDictionaryValues("employment");
    }

    @Test
    void processedTypesAcceptExactJdbcAndJsonScalarsWithoutReapplyingDisplayConversion() {
        var amount = field("amount", EntityField.FieldType.DECIMAL, false);
        var count = field("count", EntityField.FieldType.INTEGER, false);
        var day = field("day", EntityField.FieldType.DATE, false);
        var time = field("time", EntityField.FieldType.DATETIME, false);
        var enabled = field("enabled", EntityField.FieldType.BOOLEAN, false);
        var name = field("displayName", EntityField.FieldType.STRING, false);
        var plan = plan(List.of(amount, count, day, time, enabled, name), List.of(
                column("amount", "金额", Map.of("type", "AMOUNT", "scale", 2), Map.of()),
                column("count", "数量", Map.of("type", "NUMBER", "scale", 0), Map.of()),
                column("day", "日期", Map.of("type", "DATE", "format", "dd/MM/yyyy"), Map.of()),
                column("time", "时间", Map.of("type", "DATETIME"), Map.of()),
                column("enabled", "启用", Map.of("type", "BOOLEAN"), Map.of()),
                column("displayName", "姓名", Map.of("type", "STRING"), Map.of())));
        var first = Map.<String, Object>of("amount", new BigDecimal("9999999999999999.99"), "count", 1,
                "day", java.sql.Date.valueOf("2026-10-12"), "time", java.sql.Timestamp.valueOf("2026-10-12 08:30:45"),
                "enabled", 1, "display_name", " 保留空白 ");
        var second = Map.<String, Object>of("amount", "1.20", "count", "2", "day", "2026-10-12", "time", "2026-10-12T08:30:45", "enabled", true);
        assertTrue(service.validateProcessedRows(plan, processedRows(2, Map.of()), List.of(first, second)).isEmpty());
        assertEquals(" 保留空白 ", first.get("display_name"));
        assertEquals("1.20", second.get("amount"));
        var invalid = Map.<String, Object>of("amount", "1,234.50", "count", "2147483648", "day", "12/10/2026",
                "time", "2026-10-12", "enabled", "yes", "display_name", List.of("name"));
        var errors = service.validateProcessedRows(plan, processedRows(1, Map.of()), List.of(invalid));
        assertEquals(6, errors.size());
        assertTrue(errors.stream().allMatch(error -> error.columnIndex() == null));
        var dateTimeAsDate = Map.<String, Object>of("day", java.time.LocalDateTime.of(2026, 10, 12, 12, 0));
        assertEquals(1, service.validateProcessedRows(plan, processedRows(1, Map.of()), List.of(dateTimeAsDate)).size());
    }

    @Test
    void processedNumericUniquenessUsesCanonicalExactValueIncludingFormDefaults() {
        var amount = field("amount", EntityField.FieldType.DECIMAL, false); amount.setIsUnique(true);
        var plan = plan(List.of(amount), List.of(column("amount", "金额", Map.of("type", "AMOUNT", "scale", 2), Map.of())));
        var errors = service.validateProcessedRows(plan, processedRows(3, Map.of(1, "金额")), List.of(
                Map.of("amount", "1.0"), Map.of("amount", new BigDecimal("1.00")), Map.of("amount", new BigDecimal("2.00"))));
        assertEquals(List.of(2, 3), errors.stream().map(EntityExcelCodecService.Error::rowNumber).toList());
        assertTrue(errors.stream().allMatch(error -> "DUPLICATE_IN_FILE".equals(error.errorCode())));
    }

    @Test
    void stringCodecCannotBypassBoundEntityDictionary() {
        var status = field("status_value", EntityField.FieldType.SELECT, false); status.setDictType("employment");
        when(dictionaries.listDictionaryValues("employment")).thenReturn(List.of(item("01", "在职")));
        var plan = plan(List.of(status), List.of(column("status_value", "状态", Map.of("type", "STRING"), Map.of())));
        var errors = service.validateProcessedRows(plan, processedRows(2, Map.of()),
                List.of(Map.of("status_value", "01"), Map.of("status_value", "不存在")));
        assertEquals(1, errors.size());
        assertEquals(3, errors.get(0).rowNumber());
        assertTrue(errors.get(0).message().contains("启用字典"));
    }

    @Test
    void processedErrorLimitIsExactAndAppendsSingleFileLevelSentinelOnlyWhenExceeded() {
        var fields = new java.util.ArrayList<EntityField>();
        var columns = new java.util.ArrayList<ExcelSchemeDocument.Column>();
        for (int index = 0; index < 11; index++) {
            fields.add(field("required_" + index, EntityField.FieldType.STRING, true));
            columns.add(column("required_" + index, "必填" + index, Map.of("type", "STRING"), Map.of()));
        }
        var values = java.util.Collections.<Map<String, Object>>nCopies(1000, Map.of());
        var parsed = processedRows(1000, Map.of());
        var exact = service.validateProcessedRows(plan(fields.subList(0, 10), columns.subList(0, 10)), parsed, values);
        assertEquals(10000, exact.size());
        assertTrue(exact.stream().noneMatch(error -> "ERROR_LIMIT".equals(error.errorCode())));
        var exceeded = service.validateProcessedRows(plan(fields, columns), parsed, values);
        assertEquals(10001, exceeded.size());
        var sentinel = exceeded.get(10000);
        assertEquals("ERROR_LIMIT", sentinel.errorCode());
        assertEquals(0, sentinel.rowNumber());
        assertNull(sentinel.columnIndex());
        assertEquals(1, exceeded.stream().filter(error -> "ERROR_LIMIT".equals(error.errorCode())).count());
        assertThrows(IllegalArgumentException.class, () -> service.validateProcessedRows(plan(fields, columns), parsed, List.of()));
    }

    @Test
    void strictHeadersRejectReorderedAliasMissingAndExtraTitlesButPreserveAllOriginalRows() {
        var base = plan(List.of(field("name", EntityField.FieldType.STRING, true), field("amount", EntityField.FieldType.DECIMAL, false)),
                List.of(column("name", "姓名", Map.of(), Map.of()), column("amount", "金额", Map.of("type", "AMOUNT"), Map.of())));
        var plan = batchPlan(base, true, true, "STOP");
        for (List<String> titles : List.of(List.of("金额", "姓名"), List.of("姓名旧标题", "金额"),
                List.of("姓名"), List.of("姓名", "金额", "额外列"))) {
            var output = new ByteArrayOutputStream();
            ExcelUtils.writeRows(output, titles.stream().map(List::of).toList(),
                    List.of(List.of(" 张三 ", "12.00", "extra"), List.of("李四", "3.00", "tail")), "数据");
            var parsed = service.parse(plan, input(output));
            assertEquals("FILE_HEADER", parsed.errors().get(0).errorCode());
            assertTrue(parsed.rows().isEmpty());
            assertEquals(2, parsed.allRows().size());
            assertEquals(" 张三 ", parsed.allRows().get(0).rawValues().get(0));
            assertEquals("extra", parsed.allRows().get(0).rawValues().get(2));
            assertEquals(2, parsed.unimportedReasons().size());
            assertTrue(parsed.unimportedReasons().get(3).contains("表头"));
        }
    }

    @Test
    void disabledHeaderValidationUsesPositionsAndReportsCorrectFinalValidationColumn() {
        var base = plan(List.of(field("name", EntityField.FieldType.STRING, true), field("amount", EntityField.FieldType.DECIMAL, false)),
                List.of(column("name", "姓名", Map.of(), Map.of()), column("amount", "金额", Map.of("type", "AMOUNT"), Map.of("min", "0"))));
        var plan = batchPlan(base, false, true, "STOP");
        var output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("金额"), List.of("不认识"), List.of("原始备注")),
                List.of(List.of("张三", "12.00", " 备注保持 ")), "数据");
        var parsed = service.parse(plan, input(output));
        assertTrue(parsed.errors().isEmpty());
        assertEquals("张三", parsed.rows().get(0).data().get("name"));
        assertEquals(new BigDecimal("12.00"), parsed.rows().get(0).data().get("amount"));
        assertEquals(" 备注保持 ", parsed.allRows().get(0).rawValues().get(2));
        var errors = service.validateProcessedRows(plan, parsed, List.of(Map.of("name", "张三", "amount", -1)));
        assertEquals("B", errors.get(0).columnLetter());
    }

    @Test
    void firstPhysicalOrExplicitEmptyRowStopsCandidatesAndKeepsLaterOriginalRows() throws Exception {
        var base = plan(List.of(field("name", EntityField.FieldType.STRING, true)), List.of(column("name", "姓名", Map.of(), Map.of())));
        for (boolean physicalGap : List.of(false, true)) for (boolean firstDataRow : List.of(false, true)) {
            var output = new ByteArrayOutputStream();
            try (var workbook = new XSSFWorkbook()) {
                var sheet = workbook.createSheet("数据");
                sheet.createRow(0).createCell(0).setCellValue("姓名");
                int emptyIndex = firstDataRow ? 1 : 2;
                if (!firstDataRow) sheet.createRow(1).createCell(0).setCellValue("张三");
                if (!physicalGap) sheet.createRow(emptyIndex).createCell(0).setCellValue("   ");
                sheet.createRow(emptyIndex + 1).createCell(0).setCellValue("李四");
                workbook.write(output);
            }
            var stopped = service.parse(batchPlan(base, true, true, "CONTINUE"), input(output));
            assertEquals(firstDataRow ? 0 : 1, stopped.rows().size());
            assertEquals(firstDataRow ? 1 : 2, stopped.allRows().size());
            assertTrue(stopped.unimportedReasons().values().stream().allMatch(reason -> reason.contains("空行")));
            var continued = service.parse(batchPlan(base, true, false, "CONTINUE"), input(output));
            assertEquals(firstDataRow ? 1 : 2, continued.rows().size());
            assertTrue(continued.unimportedReasons().isEmpty());
        }
    }

    @Test
    void stopKeepsFirstInvalidCandidateAndContinueCollectsLaterErrors() {
        var base = plan(List.of(field("amount", EntityField.FieldType.DECIMAL, false)),
                List.of(column("amount", "金额", Map.of("type", "AMOUNT"), Map.of("min", "0"))));
        var output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("金额")), List.of(List.of("1"), List.of("bad"), List.of("3"), List.of("-4")), "数据");
        var stopped = service.parse(batchPlan(base, true, true, "STOP"), input(output));
        assertEquals(2, stopped.rows().size());
        assertEquals(4, stopped.allRows().size());
        assertEquals(1, stopped.errors().size());
        assertEquals(java.util.Set.of(4, 5), stopped.unimportedReasons().keySet());
        assertEquals("-4", stopped.allRows().get(3).rawValues().get(0));
        var continued = service.parse(batchPlan(base, true, true, "CONTINUE"), input(output));
        assertEquals(4, continued.rows().size());
        assertEquals(List.of(3, 5), continued.errors().stream().map(EntityExcelCodecService.Error::rowNumber).toList());
        assertEquals(2, continued.validRowCount());
        var subset = continued.subset(continued.rows().subList(1, 3));
        assertEquals(List.of(3), subset.errors().stream().map(EntityExcelCodecService.Error::rowNumber).toList());
    }

    @Test
    void strictHeaderOrderAllowsIgnoredMappedColumnsButRejectsRemainingColumnsInWrongOrder() {
        var base = withFormPolicy(plan(List.of(field("name", EntityField.FieldType.STRING, true),
                        field("amount", EntityField.FieldType.DECIMAL, false), field("code", EntityField.FieldType.STRING, false)),
                List.of(column("name", "姓名", Map.of(), Map.of()), column("amount", "金额", Map.of("type", "AMOUNT"), Map.of()),
                        column("code", "编号", Map.of(), Map.of()))), "IGNORE", "name", "code");
        var plan = batchPlan(base, true, true, "STOP");
        for (var titles : List.of(List.of("姓名", "金额", "编号"), List.of("姓名", "编号"))) {
            var output = new ByteArrayOutputStream();
            var values = titles.size() == 3 ? List.of("张三", "不是金额", "001") : List.of("张三", "001");
            ExcelUtils.writeRows(output, titles.stream().map(List::of).toList(), List.of(values), "数据");
            var parsed = service.parse(plan, input(output));
            assertTrue(parsed.errors().isEmpty());
            assertEquals("001", parsed.rows().get(0).data().get("code"));
            assertFalse(parsed.rows().get(0).data().containsKey("amount"));
        }
        var output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("编号"), List.of("姓名")), List.of(List.of("001", "张三")), "数据");
        assertEquals("FILE_HEADER", service.parse(plan, input(output)).errors().get(0).errorCode());
    }

    @Test
    void batchParsingDoesNotReserveUniqueValueForRowFailingAnotherColumn() {
        var name = field("name", EntityField.FieldType.STRING, true); name.setIsUnique(true);
        var base = plan(List.of(name, field("amount", EntityField.FieldType.DECIMAL, false)),
                List.of(column("name", "姓名", Map.of(), Map.of("uniqueInFile", true)),
                        column("amount", "金额", Map.of("type", "AMOUNT"), Map.of())));
        var plan = batchPlan(base, true, true, "CONTINUE");
        var output = new ByteArrayOutputStream();
        ExcelUtils.writeRows(output, List.of(List.of("姓名"), List.of("金额")),
                List.of(List.of("张三", "bad"), List.of("张三", "2")), "数据");
        var parsed = service.parse(plan, input(output));
        assertEquals(List.of(2), parsed.errors().stream().map(EntityExcelCodecService.Error::rowNumber).toList());
        var valid = parsed.rows().get(1);
        assertTrue(service.validateProcessedRows(plan, parsed.subset(List.of(valid)), List.of(valid.data())).isEmpty());
        // 仍然必须在真正有效的候选集上检查文件唯一性，不能因为解析阶段推迟而丢失规则。
        var sameValue = Map.<String, Object>of("name", "张三", "amount", new BigDecimal("2"));
        assertEquals(2, service.validateProcessedRows(plan, parsed, List.of(sameValue, sameValue)).size());
    }

    private static EntityExcelRuntimeResolver.Plan batchPlan(EntityExcelRuntimeResolver.Plan base,
            boolean validateHeader, boolean stopAtEmptyRow, String failurePolicy) {
        var old = base.scheme().document();
        var config = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "create", false, 1000,
                old.importConfig().unknownColumnPolicy(), validateHeader, stopAtEmptyRow, 100, true, true, failurePolicy);
        var doc = new ExcelSchemeDocument(old.schemaVersion(), old.schemeCode(), old.name(), old.description(), old.file(),
                config, old.exportConfig(), old.columns());
        var scheme = new ExcelResolvedScheme("entity1", "employee", "basic", doc, "hash", "", "form1");
        return new EntityExcelRuntimeResolver.Plan(base.claims(), scheme, base.list(), base.entity(), base.writeForm());
    }

    private static EntityExcelCodecService.Parsed processedRows(int count, Map<Integer, String> headers) {
        var rows = new java.util.ArrayList<EntityExcelCodecService.Row>();
        for (int index = 0; index < count; index++) rows.add(new EntityExcelCodecService.Row(1, "数据", index + 2,
                Map.of(), Map.of(4, "原始输入" + (index + 1))));
        return new EntityExcelCodecService.Parsed(rows, List.of(), count, 0, count, 0, false, headers);
    }

    private static EntityField field(String code, EntityField.FieldType type, boolean required) {
        EntityField field = new EntityField(); field.setFieldCode(code); field.setFieldName(code); field.setFieldType(type);
        field.setIsRequired(required); field.setEditable(true); field.setRuntimeReadable(true); return field;
    }
    private static DictionaryValue item(String value, String label) {
        return new DictionaryValue(value, label, true);
    }
    private static ExcelSchemeDocument.Column column(String code, String title, Map<String, Object> codec, Map<String, Object> rules) {
        return new ExcelSchemeDocument.Column(code, title, List.of(title + "旧标题"), code, true, true, true, null, codec, rules);
    }
    private static EntityExcelRuntimeResolver.Plan plan(List<EntityField> fields, List<ExcelSchemeDocument.Column> columns) {
        var doc = new ExcelSchemeDocument(1, "basic", "基础资料", "", new ExcelSchemeDocument.FileConfig("数据", 1, "REJECT"),
                new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "ATOMIC_ALL", "create", false, 1000),
                new ExcelSchemeDocument.ExportConfig(true, "数据", "数据"), columns);
        var scheme = new ExcelResolvedScheme("entity1", "employee", "basic", doc, "hash", "", "form1");
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot(); snapshot.setFields(fields);
        return new EntityExcelRuntimeResolver.Plan(null, scheme, null, new EntityPublishedSnapshotService.PinnedEntitySnapshot(snapshot, "schema"), null);
    }

    private static EntityExcelRuntimeResolver.Plan withFormPolicy(EntityExcelRuntimeResolver.Plan base, String policy, String... formFields) {
        var old = base.scheme().document();
        var document = new ExcelSchemeDocument(old.schemaVersion(), old.schemeCode(), old.name(), old.description(), old.file(),
                new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "ATOMIC_ALL", "create", false, 1000, policy),
                old.exportConfig(), old.columns());
        var scheme = new ExcelResolvedScheme("entity1", "employee", "basic", document, "hash", "", "form1");
        var form = new com.workflow.entity.form.infrastructure.persistence.record.EntityForm(); form.setId("form1");
        form.setFields(java.util.Arrays.stream(formFields).map(code -> {
            var field = new com.workflow.entity.form.infrastructure.persistence.record.EntityFormField(); field.setFieldCode(code); return field;
        }).toList());
        return new EntityExcelRuntimeResolver.Plan(null, scheme, null, base.entity(),
                new com.workflow.entity.form.application.model.ResolvedEntityFormRelease(form, "release", 1));
    }
    private static ByteArrayInputStream input(ByteArrayOutputStream output) { return new ByteArrayInputStream(output.toByteArray()); }
}
