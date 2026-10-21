package com.workflow.migration.history;

import com.workflow.contracts.history.*;
import com.workflow.contracts.history.HistoryImportModels.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryImportWorkbookTest {
    final EntityHistoryImportPort entities = mock(EntityHistoryImportPort.class);
    final HistoryImportWorkbook codec = new HistoryImportWorkbook(entities);
    final Plan plan = new Plan("导入", "old", List.of(new EntitySpec("main", "expense", true, Map.of())), "", Map.of(), Map.of());
    @BeforeEach void setup() {
        when(entities.schema("expense")).thenReturn(new EntitySchema("expense", "报销", "v1",
                List.of(new Field("name", "名称", "STRING", true, null)), List.of()));
    }
    MockMultipartFile file(XSSFWorkbook book) throws IOException {
        var output = new ByteArrayOutputStream(); book.write(output);
        return new MockMultipartFile("file", "history.xlsx", "application/octet-stream", output.toByteArray());
    }
    XSSFWorkbook book() throws IOException { return new XSSFWorkbook(new ByteArrayInputStream(codec.template(plan, "entities"))); }

    @Test void bilingualEntityHeadersUsePublishedNamesAndSurviveLabelChanges() throws Exception {
        when(entities.schema("expense")).thenReturn(new EntitySchema("expense", "报销", "v1",
                List.of(new Field("name", "名称", "STRING", true, null),
                        new Field("amount", "金额（元）", "DECIMAL", true, null)), List.of()));
        try (var book = book()) {
            var header = book.getSheetAt(0).getRow(0);
            assertEquals("原业务记录ID（sourceRecordId）", header.getCell(0).getStringCellValue());
            assertEquals("提交组（groupKey）", header.getCell(1).getStringCellValue());
            assertEquals("名称（name）", header.getCell(2).getStringCellValue());
            assertEquals("金额（元）（amount）", header.getCell(3).getStringCellValue());
            var row = book.getSheetAt(0).createRow(1);
            String[] values = {"0001", "G1", "报销单", "123.45"};
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            // 两个字段后来同名，仍必须以英文编码识别，不能按中文标签猜测目标列。
            when(entities.schema("expense")).thenReturn(new EntitySchema("expense", "报销", "v2",
                    List.of(new Field("name", "内容", "STRING", true, null),
                            new Field("amount", "内容", "DECIMAL", true, null)), List.of()));
            var parsed = codec.parse(plan, "entities", file(book)).entities().get(0);
            assertEquals("0001", parsed.sourceId());
            assertEquals(Map.of("name", "报销单", "amount", "123.45"), parsed.values());
        }
    }

    @Test void bilingualHistoryHeadersRoundTripAllSummaryAndApprovalColumns() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(codec.template(plan, "history")))) {
            String[] summaries = {"原流程实例ID（sourceInstanceId）", "来源实体标识（entityKey）", "原业务记录ID（sourceRecordId）",
                    "流程名称（name）", "发起人（initiator）", "开始时间（startedAt）", "结束时间（endedAt）", "结束结果（result）", "预期审批记录数（expectedEvents）"};
            String[] approvals = {"原审批记录ID（sourceEventId）", "原流程实例ID（sourceInstanceId）", "旧节点（sourceNode）",
                    "审批人（actor）", "审批操作（action）", "审批意见（comment）", "办理时间（handledAt）", "审批顺序（sequence）"};
            for (int i = 0; i < summaries.length; i++) assertEquals(summaries[i], book.getSheetAt(0).getRow(0).getCell(i).getStringCellValue());
            for (int i = 0; i < approvals.length; i++) assertEquals(approvals[i], book.getSheetAt(1).getRow(0).getCell(i).getStringCellValue());
            var summary = book.getSheetAt(0).createRow(1);
            String[] summaryValues = {"P01", "main", "0001", "历史报销", "张三", "2020-01-01 10:00:00", "2020-01-02 10:00:00", "APPROVED", "1"};
            for (int i = 0; i < summaryValues.length; i++) summary.createCell(i).setCellValue(summaryValues[i]);
            var event = book.getSheetAt(1).createRow(1);
            String[] eventValues = {"E01", "P01", "经理", "李四", "通过", "同意", "2020-01-02 09:00:00", "1"};
            for (int i = 0; i < eventValues.length; i++) event.createCell(i).setCellValue(eventValues[i]);
            var input = codec.parse(plan, "history", file(book));
            assertEquals(new InstanceRow("P01", "main", "0001", "历史报销", "张三", "2020-01-01 10:00:00", "2020-01-02 10:00:00", "APPROVED", 1), input.instances().get(0));
            assertEquals(new EventRow("E01", "P01", "经理", "李四", "通过", "同意", "2020-01-02 09:00:00", 1), input.events().get(0));
        }
    }

    @Test void acceptsLegacyEnglishTemplatesAndHalfWidthParentheses() throws Exception {
        for (String[] headers : List.of(new String[]{"sourceRecordId", "groupKey", "name"},
                new String[]{"原业务记录ID(sourceRecordId)", "提交组(groupKey)", "名称(name)"})) {
            try (var book = book()) {
                for (int i = 0; i < headers.length; i++) book.getSheetAt(0).getRow(0).getCell(i).setCellValue(headers[i]);
                var row = book.getSheetAt(0).createRow(1);
                row.createCell(0).setCellValue("0001"); row.createCell(1).setCellValue("G1"); row.createCell(2).setCellValue("旧模板");
                assertEquals("旧模板", codec.parse(plan, "entities", file(book)).entities().get(0).values().get("name"));
            }
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(codec.template(plan, "history")))) {
            String[] summaryHeaders = {"sourceInstanceId", "entityKey", "sourceRecordId", "name", "initiator", "startedAt", "endedAt", "result", "expectedEvents"};
            String[] eventHeaders = {"sourceEventId", "sourceInstanceId", "sourceNode", "actor", "action", "comment", "handledAt", "sequence"};
            for (int i = 0; i < summaryHeaders.length; i++) book.getSheetAt(0).getRow(0).getCell(i).setCellValue(summaryHeaders[i]);
            for (int i = 0; i < eventHeaders.length; i++) book.getSheetAt(1).getRow(0).getCell(i).setCellValue(eventHeaders[i]);
            String[] values = {"P1", "main", "0001", "历史审批", "张三", "", "", "COMPLETED_UNKNOWN", "0"};
            var row = book.getSheetAt(0).createRow(1);
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            assertEquals("P1", codec.parse(plan, "history", file(book)).instances().get(0).sourceId());
        }
    }

    @Test void rejectsUnknownOrDuplicateCodesAcrossHeaderFormats() throws Exception {
        for (String extra : List.of("name", "别名（name）", "别名(name)", "名称（processInstanceId）", "名称")) {
            try (var book = book()) {
                book.getSheetAt(0).getRow(0).createCell(3).setCellValue(extra);
                var error = assertThrows(IllegalArgumentException.class, () -> codec.parse(plan, "entities", file(book)));
                assertTrue(error.getMessage().contains("未知或重复表头"));
            }
        }
    }

    @Test void keepsLeadingZeroIdsAndReadsPastBlankRows() throws Exception {
        try (var book = book()) {
            var first = book.getSheetAt(0).createRow(1); first.createCell(0).setCellValue("0001"); first.createCell(1).setCellValue("G1"); first.createCell(2).setCellValue("第一笔");
            var later = book.getSheetAt(0).createRow(4); later.createCell(0).setCellValue("0002"); later.createCell(1).setCellValue("G2"); later.createCell(2).setCellValue("第二笔");
            var rows = codec.parse(plan, "entities", file(book)).entities();
            assertEquals(List.of("0001", "0002"), rows.stream().map(EntityRow::sourceId).toList());
        }
    }
    @Test void rejectsFormulasWithCoordinates() throws Exception {
        try (var book = book()) {
            book.getSheetAt(0).createRow(3).createCell(0).setCellFormula("1+1");
            var error = assertThrows(IllegalArgumentException.class, () -> codec.parse(plan, "entities", file(book)));
            assertTrue(error.getMessage().contains("main 第4行"));
        }
    }
    @Test void rejectsUnknownHeadersAndStraySheets() throws Exception {
        try (var book = book()) {
            book.getSheetAt(0).getRow(0).createCell(3).setCellValue("processInstanceId");
            assertThrows(IllegalArgumentException.class, () -> codec.parse(plan, "entities", file(book)));
        }
        try (var book = book()) {
            book.createSheet("错误实体");
            assertThrows(IllegalArgumentException.class, () -> codec.parse(plan, "entities", file(book)));
        }
    }
    @Test void historyTemplateHasBothRequiredSheetsAndAcceptsZeroEvents() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(codec.template(plan, "history")))) {
            String[] values = {"P1", "main", "0001", "历史审批", "张三", "", "", "COMPLETED_UNKNOWN", "0"};
            var row = book.getSheet("流程概要").createRow(1);
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            var input = codec.parse(plan, "history", file(book));
            assertEquals(1, input.instances().size()); assertTrue(input.events().isEmpty());
        }
    }
}
