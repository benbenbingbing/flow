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
