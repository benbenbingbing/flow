package com.workflow.entity.excel.runtime;

import static com.workflow.entity.excel.persistence.ExcelMapperTestSupport.mapper;
import com.workflow.entity.excel.infrastructure.persistence.mapper.EntityExcelJobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.EntityDataMutationValidator;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.CurrentFormRuntimeContext;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 真实工作簿、H2 事务和参与事务的失败代理覆盖分批导入。
 * 另开数据库连接观察提交可见性，防止“逐行提交但计数分批”的实现误通过。
 */
class EntityExcelBatchImportTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final EntityExcelRuntimeResolver resolver = mock(EntityExcelRuntimeResolver.class);
    private final EntityExcelFileStore files = mock(EntityExcelFileStore.class);
    private final EntityDataActionService actions = mock(EntityDataActionService.class);
    private final PublishedFormSubmissionService submissions = mock(PublishedFormSubmissionService.class);
    private final EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
    private final CurrentFormRuntimeContext formRuntime = mock(CurrentFormRuntimeContext.class);
    private final EntityDataMutationValidator entityValidator = mock(EntityDataMutationValidator.class);
    private final Map<String, byte[]> storedFiles = new LinkedHashMap<>();
    private final Map<String, String> storedNames = new LinkedHashMap<>();
    private JdbcTemplate jdbc;
    private EntityExcelJobService service;
    private EntityExcelRuntimeResolver.Plan plan;
    private TransactionalRowWriter writer;
    private EntityExcelCodecService codec;

    @BeforeEach void setup() throws Exception {
        UserContext.setCurrentUser("alice", "alice");
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:excel_batch_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new FileSystemResource(Path.of(
                "../workflow-db-migrator/src/main/resources/db/migration/V115__entity_excel_configuration_and_jobs.sql")))
                .execute(source);
        jdbc.execute("CREATE TABLE imported_records(id VARCHAR(100) PRIMARY KEY, name VARCHAR(100), memo VARCHAR(100))");
        var tx = new DataSourceTransactionManager(source);
        writer = new TransactionalRowWriter(source, jdbc);
        var factory = new ProxyFactory(writer);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        var transactionalWriter = (TransactionalRowWriter) factory.getProxy();
        doAnswer(call -> transactionalWriter.save(call.getArgument(0), call.getArgument(1)))
                .when(actions).createExcelRow(any(), anyString());
        when(submissions.previewSideEffectFreeForm(any(), any(), any(), any(), isNull(), eq("create"), anyMap(), any(), any()))
                .thenAnswer(call -> call.getArgument(6));
        when(definitions.findByEntityCodeForUpdate("employee")).thenReturn(Optional.of(new EntityDefinition()));
        when(formRuntime.graphHash(anyString())).thenReturn("form-hash");
        when(files.put(any(), anyString(), anyString(), eq("alice"))).thenAnswer(call -> {
            String id = "file_" + storedFiles.size();
            storedFiles.put(id, Files.readAllBytes(call.getArgument(0)));
            storedNames.put(id, call.getArgument(1));
            return id;
        });
        when(files.metadata(anyString())).thenAnswer(call -> Map.of(
                "original_name", storedNames.get(call.getArgument(0)), "content_type", EntityExcelFileStore.XLSX));
        codec = spy(new EntityExcelCodecService(mock(SysDictItemService.class), new EntityFieldValidationRuleService(mapper),
                mock(com.workflow.entity.definition.application.EntityStatusService.class)));
        service = new EntityExcelJobService(resolver, codec, new ExcelActionTokenService(mapper, "batch-test"),
                new EntityExcelJobRepository(mapper(jdbc.getDataSource(), EntityExcelJobMapper.class), mapper, tx), files, mock(EntityExcelAtomicImportPolicy.class),
                mock(EntityActionCapabilityService.class), actions, mock(EntityDataDynamicService.class),
                mock(EntityDataListConfigService.class), submissions, entityValidator,
                new EntityRuntimeRecordMapper(mapper), definitions, mock(EntityFormPublishedMapper.class),
                mock(UiConfigReleaseService.class), formRuntime, tx, mapper);
        configure(Map.of());
    }

    @AfterEach void clearUser() { UserContext.clear(); }

    @Test void commitsConfiguredBatchSizeAndFinalRemainderInsteadOfEachRow() throws Exception {
        var result = importRows("batch-boundaries", "one", "two", "three", "four", "five");

        assertThat(result.get("status")).isEqualTo("SUCCEEDED");
        assertSaved(result, 5);
        assertThat(writer.observedCommittedCounts).containsExactly(0, 0, 2, 2, 4);
        assertThat(writer.committedNames()).containsExactlyInAnyOrder("one", "two", "three", "four", "five");
    }

    @Test void stopKeepsCommittedBatchesAndSuccessfulPrefixButNeverAttemptsLaterRows() throws Exception {
        writer.failNames = Set.of("bad");
        var result = importRows("stop", "one", "two", "three", "bad", "later");

        assertThat(result.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(result, 3);
        assertThat(writer.attemptedNames).doesNotContain("later");
        assertThat(writer.committedNames()).containsExactlyInAnyOrder("one", "two", "three");
        assertThat(result.get("errors").toString()).contains("写入被拒绝：bad");
    }

    @Test void continueSurvivesRollbackOnlyFailureAndSavesSubsequentRowsExactlyOnce() throws Exception {
        configure(Map.of("failurePolicy", "CONTINUE"));
        writer.failNames = Set.of("bad", "bad-too");
        var result = importRows("continue", "one", "bad", "three", "bad-too", "five");

        assertThat(result.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(result, 3);
        assertThat(writer.committedNames()).containsExactlyInAnyOrder("one", "three", "five");
        assertThat(result.get("errors").toString()).contains("bad", "bad-too");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM imported_records", Integer.class)).isEqualTo(3);
    }

    @Test void replayOfPartiallySuccessfulRequestCannotInsertAgain() throws Exception {
        writer.failNames = Set.of("bad");
        var upload = workbook("one", "two", "bad", "later");
        var result = service.importFile("token", "replay", upload, false);
        int attempts = writer.attemptedNames.size();

        var replayed = service.importFile("token", "replay", upload, false);

        assertThat(replayed.get("jobId")).isEqualTo(result.get("jobId"));
        assertThat(replayed.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(replayed, 2);
        assertThat(writer.attemptedNames).hasSize(attempts);
    }

    @Test void precheckValidatesWithoutSavingAndRejectsReuseAsImport() throws Exception {
        var upload = workbook("one", "two");
        var result = service.importFile("token", "check", upload, true);

        assertThat(result.get("status")).isEqualTo("VALIDATED");
        assertSaved(result, 0);
        assertThat(writer.attemptedNames).isEmpty();
        verifyNoInteractions(actions);
        assertThatThrownBy(() -> service.importFile("token", "check", upload, false))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void disabledPrecheckCannotBeInvokedByCallingEndpointDirectly() throws Exception {
        configure(Map.of("precheckEnabled", false));

        assertThatThrownBy(() -> service.importFile("token", "check-disabled", workbook("one"), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("预校验");
        assertThat(writer.committedNames()).isEmpty();
        verifyNoInteractions(actions);
    }

    @Test void stopOnSchemeValidationErrorSavesOnlyEarlierRows() throws Exception {
        var result = importRows("rule-stop", "one", "", "later");

        assertThat(result.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(result, 1);
        assertThat(writer.committedNames()).containsExactly("one");
        assertThat(writer.attemptedNames).doesNotContain("later", "");
        assertThat(result.get("errors").toString()).contains("3");
    }

    @Test void continueOnSchemeValidationErrorSavesLaterRows() throws Exception {
        configure(Map.of("failurePolicy", "CONTINUE"));
        var result = importRows("rule-continue", "one", "", "later");

        assertThat(result.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(result, 2);
        assertThat(writer.committedNames()).containsExactlyInAnyOrder("one", "later");
        assertThat(writer.attemptedNames).doesNotContain("");
    }

    @Test void precheckReportsInvalidDataWithoutWritingValidRowsEither() throws Exception {
        configure(Map.of("failurePolicy", "CONTINUE"));
        var result = service.importFile("token", "check-invalid", workbook("one", "", "later"), true);

        assertThat(result.get("status")).isEqualTo("REJECTED");
        assertSaved(result, 0);
        verifyNoInteractions(actions);
        assertThat(result.get("errors").toString()).contains("3");
    }

    @Test void headerMismatchRejectsWholeFileBeforeAnyBusinessWrite() throws Exception {
        byte[] source = workbook("one", "two").getBytes();
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(source)); var out = new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(0).getCell(0).setCellValue("错误表头");
            book.write(out);
            var result = service.importFile("token", "bad-head", new MockMultipartFile("file", "bad-head.xlsx",
                    EntityExcelFileStore.XLSX, out.toByteArray()), false);

            assertThat(result.get("status")).isEqualTo("REJECTED");
            assertSaved(result, 0);
            verifyNoInteractions(actions);
            assertThat(result.get("errors").toString()).contains("表头");
            String report = jdbc.queryForObject("SELECT error_file_id FROM entity_excel_job WHERE id=?", String.class, result.get("jobId"));
            try (var reportBook = WorkbookFactory.create(new ByteArrayInputStream(storedFiles.get(report)))) {
                assertThat(reportBook.getSheetAt(0).getLastRowNum()).isEqualTo(2);
                assertThat(reportBook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("one");
                assertThat(reportBook.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).isEqualTo("two");
            }
        }
    }

    @Test void disabledUnimportedReportReturnsErrorsWithoutCreatingDownloadFile() throws Exception {
        configure(Map.of("returnUnimported", false));
        writer.failNames = Set.of("bad");
        var result = importRows("without-report", "one", "bad", "later");

        assertSaved(result, 1);
        assertThat(result.get("errors").toString()).contains("bad");
        assertThat(jdbc.queryForObject("SELECT error_file_id FROM entity_excel_job WHERE id=?",
                String.class, result.get("jobId"))).isNull();
        assertThat(storedFiles).hasSize(1);
    }

    @Test void unimportedReportKeepsWholeOriginalRowsAndAddsOnlyRowAndReason() throws Exception {
        // 关闭表头校验后允许保留方案之外的原文件列；它不应进入业务数据，但必须完整进入未导入报告。
        configure(Map.of("validateHeader", false));
        writer.failNames = Set.of("bad");
        var result = service.importFile("token", "report-original", richWorkbook(), false);

        assertThat(result.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(result, 1);
        String report = jdbc.queryForObject("SELECT error_file_id FROM entity_excel_job WHERE id=?",
                String.class, result.get("jobId"));
        assertThat(report).isNotBlank();
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(storedFiles.get(report)))) {
            var sheet = book.getSheetAt(0);
            var formatter = new DataFormatter();
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            assertThat(text(sheet.getRow(0), formatter)).containsExactly("姓名", "备注", "原编号", "原日期", "行数", "原因");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("  bad  ");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("  原始备注  ");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("001");
            assertThat(sheet.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(3))).isEqualTo("2026-10-12");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(4))).isEqualTo("3");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).contains("bad");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("  later  ");
            assertThat(formatter.formatCellValue(sheet.getRow(2).getCell(4))).isEqualTo("4");
            assertThat(sheet.getRow(2).getCell(5).getStringCellValue()).isNotBlank();
        }
    }

    @Test void defaultEmptyRowCutoffSavesOnlyPrefixAndReportsLaterOriginalRows() throws Exception {
        var result = service.importFile("token", "empty-cutoff", workbook("one", null, "later"), false);

        assertSaved(result, 1);
        assertThat(writer.attemptedNames).containsExactly("one");
        String report = jdbc.queryForObject("SELECT error_file_id FROM entity_excel_job WHERE id=?", String.class, result.get("jobId"));
        assertThat(report).isNotBlank();
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(storedFiles.get(report)))) {
            assertThat(book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("later");
            assertThat(text(book.getSheetAt(0).getRow(1), new DataFormatter())).anyMatch(value -> value.contains("空行"));
        }
    }

    @Test void disablingEmptyRowCutoffImportsNonemptyRowsAfterGap() throws Exception {
        configure(Map.of("stopAtEmptyRow", false));
        var result = service.importFile("token", "empty-continue", workbook("one", null, "later"), false);

        assertThat(result.get("status")).isEqualTo("SUCCEEDED");
        assertSaved(result, 2);
        assertThat(writer.committedNames()).containsExactlyInAnyOrder("one", "later");
    }

    @Test void cleanupKeepsAlreadyCommittedRowsAndTheirPersistedCount() throws Exception {
        var result = importRows("interrupted-after-commit", "one", "two");
        String jobId = String.valueOf(result.get("jobId"));
        String countersBefore = jdbc.queryForObject("SELECT counters_json FROM entity_excel_job WHERE id=?", String.class, jobId);
        // 模拟进度随业务提交完成后进程中断，任务停留 RUNNING，次日才被回收。
        jdbc.update("UPDATE entity_excel_job SET status='RUNNING',created_at=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusHours(25), jobId);
        var tx = new DataSourceTransactionManager(jdbc.getDataSource());

        new EntityExcelJobCleanup(mapper(jdbc.getDataSource(), EntityExcelJobMapper.class), files, tx).clean();

        var job = jdbc.queryForMap("SELECT * FROM entity_excel_job WHERE id=?", jobId);
        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat(job.get("counters_json")).isEqualTo(countersBefore);
        assertThat(mapper.readTree(String.valueOf(job.get("counters_json"))).path("savedRowCount").asInt()).isEqualTo(2);
        assertThat(job.get("error_message").toString()).contains("已提交记录保留");
        assertThat(job.get("input_file_id")).isNull();
        assertThat(writer.committedNames()).containsExactlyInAnyOrder("one", "two");
        assertThatThrownBy(() -> new EntityExcelJobRepository(mapper(jdbc.getDataSource(), EntityExcelJobMapper.class), mapper, tx).lockRunning(jobId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("任务已结束");
    }

    @Test void precheckDetectsFormNormalizedDuplicatesAcrossConfiguredSubmissionBatches() throws Exception {
        configure(Map.of("batchSize", 1, "failurePolicy", "CONTINUE"));
        enableUniqueNameRule();
        when(submissions.previewSideEffectFreeForm(any(), any(), any(), any(), isNull(), eq("create"), anyMap(), any(), any()))
                .thenAnswer(call -> {
                    Map<String, Object> values = new LinkedHashMap<>(call.getArgument(6));
                    values.put("name", canonicalName(values.get("name").toString()));
                    return values;
                });

        var result = service.importFile("token", "check-normalized-duplicate", workbook("Alice-A", "Alice-B"), true);

        assertThat(result.get("status")).isEqualTo("REJECTED");
        assertSaved(result, 0);
        assertThat(result.get("errors").toString()).contains("DUPLICATE_IN_FILE");
        assertThat(((Number) result.get("invalidRowCount")).intValue()).isEqualTo(2);
        verifyNoInteractions(actions);
    }

    @Test void authoritativeCrossBatchDuplicateRollsBackOnlyNewBatchAndStopsFollowingRows() throws Exception {
        configure(Map.of("batchSize", 1));
        enableUniqueNameRule();
        writer.nameTransform = EntityExcelBatchImportTest::canonicalName;

        var result = importRows("save-normalized-stop", "Alice-A", "Alice-B", "Later");

        assertThat(result.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(result, 1);
        assertThat(writer.committedNames()).containsExactly("alice");
        assertThat(writer.attemptedNames).doesNotContain("later");
        assertThat(result.get("errors").toString()).contains("DUPLICATE_IN_FILE");
    }

    @Test void authoritativeCrossBatchDuplicateCanContinueWithoutRewritingCommittedRows() throws Exception {
        configure(Map.of("batchSize", 1, "failurePolicy", "CONTINUE"));
        enableUniqueNameRule();
        writer.nameTransform = EntityExcelBatchImportTest::canonicalName;

        var result = importRows("save-normalized-continue", "Alice-A", "Alice-B", "Later");

        assertThat(result.get("status")).isEqualTo("PARTIAL_SUCCESS");
        assertSaved(result, 2);
        assertThat(writer.committedNames()).containsExactlyInAnyOrder("alice", "later");
        assertThat(writer.attemptedNames).containsExactly("alice", "alice", "later");
        assertThat(result.get("errors").toString()).contains("DUPLICATE_IN_FILE");
    }

    @Test void unimportedReportPreserves1904DateSystemForXlsAndXlsxSources() throws Exception {
        configure(Map.of("validateHeader", false));
        writer.failNames = Set.of("bad");
        double serial = org.apache.poi.ss.usermodel.DateUtil.getExcelDate(java.time.LocalDate.of(2026, 10, 12), true);
        for (boolean xls : List.of(false, true)) {
            byte[] bytes;
            try (org.apache.poi.ss.usermodel.Workbook book = xls ? new org.apache.poi.hssf.usermodel.HSSFWorkbook() : new XSSFWorkbook();
                 var out = new ByteArrayOutputStream()) {
                if (book instanceof XSSFWorkbook xlsx) xlsx.getCTWorkbook().getWorkbookPr().setDate1904(true);
                else ((org.apache.poi.hssf.record.DateWindow1904Record) ((org.apache.poi.hssf.usermodel.HSSFWorkbook) book)
                        .getInternalWorkbook().findFirstRecordBySid(org.apache.poi.hssf.record.DateWindow1904Record.sid)).setWindowing((short) 1);
                var sheet = book.createSheet("数据"); var head = sheet.createRow(0);
                head.createCell(0).setCellValue("姓名"); head.createCell(1).setCellValue("备注"); head.createCell(2).setCellValue("原日期");
                var row = sheet.createRow(1); row.createCell(0).setCellValue("bad"); row.createCell(1).setCellValue("原样");
                var style = book.createCellStyle(); style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
                row.createCell(2).setCellValue(serial); row.getCell(2).setCellStyle(style);
                book.write(out); bytes = out.toByteArray();
            }

            var result = service.importFile("token", "date-1904-" + xls,
                    new MockMultipartFile("file", xls ? "input.xls" : "input.xlsx",
                            xls ? "application/vnd.ms-excel" : EntityExcelFileStore.XLSX, bytes), false);

            assertSaved(result, 0);
            String reportId = jdbc.queryForObject("SELECT error_file_id FROM entity_excel_job WHERE id=?", String.class, result.get("jobId"));
            assertThat(reportId).isNotBlank();
            try (var report = new XSSFWorkbook(new ByteArrayInputStream(storedFiles.get(reportId)))) {
                assertThat(report.isDate1904()).isTrue();
                var date = report.getSheetAt(0).getRow(1).getCell(2);
                assertThat(date.getNumericCellValue()).isEqualTo(serial);
                assertThat(new DataFormatter().formatCellValue(date)).isEqualTo("2026-10-12");
            }
        }
    }

    @Test void truncatedErrorsCannotMakeUnidentifiedInvalidNullableCellsEligibleForSaving() throws Exception {
        configure(Map.of("failurePolicy", "CONTINUE"));
        doAnswer(call -> {
            var parsed = (EntityExcelCodecService.Parsed) call.callRealMethod();
            // 模拟超出明细预算后，一行的可空备注转换失败，data 中已无该值，普通终检无法再次定位。
            var rows = parsed.rows().stream().map(row -> {
                var data = new LinkedHashMap<>(row.data()); data.remove("memo");
                return new EntityExcelCodecService.Row(row.sheetNo(), row.sheetName(), row.rowNumber(), data, row.rawValues());
            }).toList();
            return new EntityExcelCodecService.Parsed(rows, List.of(), parsed.dataRowCount(), parsed.skippedRowCount(),
                    1, 1, true, parsed.headers(), parsed.allRows(), parsed.unimportedReasons());
        }).when(codec).parse(any(), any());

        var result = importRows("truncated-errors", "one", "two");

        assertThat(result.get("status")).isEqualTo("REJECTED");
        assertSaved(result, 0);
        assertThat(result.get("errorsTruncated")).isEqualTo(true);
        assertThat(result.get("errors").toString()).contains("ERROR_LIMIT");
        verifyNoInteractions(actions);
    }

    @Test void rejectedUnknownFormColumnStillReturnsEveryOriginalRow() throws Exception {
        configure(Map.of("unknownColumnPolicy", "REJECT"));
        // memo 已列入实体与 Excel 方案，但不在表单，解析器会在读文件之前拒绝配置。
        var known = new EntityFormField(); known.setFieldCode("name");
        plan.writeForm().form().setFields(List.of(known));

        var result = importRows("unknown-rejected-report", "  one  ", "two");

        assertThat(result.get("status")).isEqualTo("REJECTED");
        assertSaved(result, 0);
        verifyNoInteractions(actions);
        assertThat(result.get("errors").toString()).contains("未知列", "memo");
        String reportId = jdbc.queryForObject("SELECT error_file_id FROM entity_excel_job WHERE id=?", String.class, result.get("jobId"));
        assertThat(reportId).isNotBlank();
        try (var report = WorkbookFactory.create(new ByteArrayInputStream(storedFiles.get(reportId)))) {
            var sheet = report.getSheetAt(0); var formatter = new DataFormatter();
            assertThat(text(sheet.getRow(0), formatter)).containsExactly("姓名", "备注", "行数", "原因");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("  one  ");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("two");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).contains("未知列");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).contains("未知列");
        }
    }

    @SuppressWarnings("unchecked")
    @Test void positionalImportReportsEntityFieldFailureAtItsActualFileColumn() throws Exception {
        configure(Map.of("validateHeader", false));
        when(entityValidator.validatePublishedFieldsBatch(eq("employee"), anyList())).thenReturn(List.of(
                new EntityDataMutationValidator.BatchFieldError(0, "memo", "FIELD_VALIDATION", "备注不符合实体规则")));
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(workbook("one").getBytes()));
             var out = new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(0).getCell(0).setCellValue("上传者的第一列");
            book.getSheetAt(0).getRow(0).getCell(1).setCellValue("上传者的第二列");
            book.write(out);

            var result = service.importFile("token", "positional-field-error", new MockMultipartFile("file", "positional.xlsx",
                    EntityExcelFileStore.XLSX, out.toByteArray()), true);

            assertThat(result.get("status")).isEqualTo("REJECTED");
            assertSaved(result, 0);
            assertThat((List<Map<String, Object>>) result.get("errors")).singleElement().satisfies(error ->
                    assertThat(error).containsEntry("fieldCode", "memo").containsEntry("columnIndex", 1)
                            .containsEntry("columnLetter", "B").containsEntry("rowNumber", 2));
            verifyNoInteractions(actions);
        }
    }

    /** 文件规则作用于表单和正式保存后的最终值，不能仅凭原始文本不同就当作不重复。 */
    private void enableUniqueNameRule() {
        var old = plan.scheme().document();
        var columns = old.columns().stream().map(column -> !"name".equals(column.fieldCode()) ? column
                : new ExcelSchemeDocument.Column(column.key(), column.header(), column.headerAliases(), column.fieldCode(),
                        column.importEnabled(), column.exportEnabled(), column.requiredHeader(), column.width(), column.codec(),
                        Map.<String, Object>of("required", true, "uniqueInFile", true))).toList();
        var document = new ExcelSchemeDocument(old.schemaVersion(), old.schemeCode(), old.name(), old.description(),
                old.file(), old.importConfig(), old.exportConfig(), columns);
        plan = new EntityExcelRuntimeResolver.Plan(plan.claims(), new ExcelResolvedScheme("entity1", "employee", "basic",
                document, "scheme", "default", "form1"), plan.list(), plan.entity(), plan.writeForm());
        when(resolver.resolve(eq("token"), anyString())).thenReturn(plan);
        when(resolver.originKey(plan)).thenReturn("list1:import");
        when(resolver.filters(plan)).thenReturn(Map.of());
    }

    private static String canonicalName(String name) {
        return name.startsWith("Alice-") ? "alice" : name.toLowerCase(java.util.Locale.ROOT);
    }

    private Map<String, Object> importRows(String request, String... names) throws Exception {
        return service.importFile("token", request, workbook(names), false);
    }

    private void assertSaved(Map<String, Object> result, int count) {
        assertThat(((Number) result.get("savedRowCount")).intValue()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isEqualTo(count);
    }

    /** 使用公开 JSON 协议构造新版方案，避免测试依赖 record 构造参数的排列。 */
    private void configure(Map<String, Object> overrides) throws Exception {
        var document = mapper.readTree("""
                {"schemaVersion":1,"schemeCode":"basic","name":"员工",
                 "file":{"sheetName":"数据","headRowNumber":1,"unknownColumnPolicy":"REJECT"},
                 "importConfig":{"enabled":true,"writePolicy":"CREATE_ONLY","transactionPolicy":"BATCH",
                  "writeFormKey":"create","startProcess":false,"maxRows":1000,"unknownColumnPolicy":"DIRECT",
                  "validateHeader":true,"stopAtEmptyRow":true,"batchSize":2,"returnUnimported":true,
                  "precheckEnabled":true,"failurePolicy":"STOP"},
                 "exportConfig":{"enabled":true,"fileNamePattern":"员工","sheetName":"数据"},
                 "columns":[
                  {"key":"name","header":"姓名","fieldCode":"name","importEnabled":true,"exportEnabled":true,
                   "requiredHeader":true,"codec":{"type":"STRING","trim":true},"validation":{"required":true}},
                  {"key":"memo","header":"备注","fieldCode":"memo","importEnabled":true,"exportEnabled":true,
                   "codec":{"type":"STRING","trim":true}}]}
                """);
        var config = (com.fasterxml.jackson.databind.node.ObjectNode) document.get("importConfig");
        overrides.forEach((key, value) -> config.set(key, mapper.valueToTree(value)));
        var origin = new UiEventExecuteRequest();
        origin.setConfigType("LIST"); origin.setConfigId("list1"); origin.setEntityCode("employee");
        origin.setTargetKey("import"); origin.setInput(Map.of("filters", Map.of()));
        var claims = new ExcelActionTokenService.Claims(1, "alice", Instant.now().getEpochSecond() + 900,
                "BUILT_IN", origin, "source", "IMPORT", "SELECTED", "DEFAULT", "basic", "scheme", "default",
                "entity", "form", List.of("IMPORT", "CHECK", "TEMPLATE"));
        var form = new EntityForm(); form.setId("form1");
        var snapshot = new EntityPublishedSnapshot();
        List<EntityField> fields = new ArrayList<>(); List<EntityFormField> formFields = new ArrayList<>();
        for (String code : List.of("name", "memo")) {
            var field = new EntityField(); field.setFieldCode(code); field.setFieldName(code);
            field.setFieldType(EntityField.FieldType.STRING); fields.add(field);
            var formField = new EntityFormField(); formField.setFieldCode(code); formFields.add(formField);
        }
        snapshot.setFields(fields); form.setFields(formFields);
        plan = new EntityExcelRuntimeResolver.Plan(claims, new ExcelResolvedScheme("entity1", "employee", "basic",
                mapper.treeToValue(document, ExcelSchemeDocument.class), "scheme", "default", "form1"), null,
                new EntityPublishedSnapshotService.PinnedEntitySnapshot(snapshot, "entity"),
                new ResolvedEntityFormRelease(form, "release", 1));
        when(resolver.resolve(eq("token"), anyString())).thenReturn(plan);
        when(resolver.originKey(plan)).thenReturn("list1:import");
        when(resolver.filters(plan)).thenReturn(Map.of());
    }

    private MockMultipartFile workbook(String... names) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("数据");
            var head = sheet.createRow(0); head.createCell(0).setCellValue("姓名"); head.createCell(1).setCellValue("备注");
            for (int i = 0; i < names.length; i++) {
                if (names[i] == null) continue;
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(names[i]); row.createCell(1).setCellValue("memo " + i);
            }
            book.write(out);
            return new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, out.toByteArray());
        }
    }

    private MockMultipartFile richWorkbook() throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("数据"); var head = sheet.createRow(0);
            var titles = List.of("姓名", "备注", "原编号", "原日期");
            for (int i = 0; i < titles.size(); i++) head.createCell(i).setCellValue(titles.get(i));
            var dateStyle = book.createCellStyle(); dateStyle.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            var names = List.of("one", "  bad  ", "  later  ");
            for (int i = 0; i < names.size(); i++) {
                var row = sheet.createRow(i + 1); row.createCell(0).setCellValue(names.get(i));
                row.createCell(1).setCellValue("  原始备注  "); row.createCell(2).setCellValue("001");
                var date = row.createCell(3); date.setCellValue(java.time.LocalDate.of(2026, 10, 12)); date.setCellStyle(dateStyle);
            }
            book.write(out);
            return new MockMultipartFile("file", "original.xlsx", EntityExcelFileStore.XLSX, out.toByteArray());
        }
    }

    private List<String> text(org.apache.poi.ss.usermodel.Row row, DataFormatter formatter) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) result.add(formatter.formatCellValue(row.getCell(i)));
        return result;
    }

    /**
     * REQUIRED 代理故意先写 SQL 再失败；异常会把共享事务标成 rollback-only。
     * 这能检出捕获异常后继续提交同一事务的错误处理方式，并验证失败 SQL 一并回滚。
     */
    static class TransactionalRowWriter {
        private final JdbcDataSource source;
        private final JdbcTemplate jdbc;
        final List<Integer> observedCommittedCounts = new ArrayList<>();
        final List<String> attemptedNames = new ArrayList<>();
        Set<String> failNames = Set.of();
        java.util.function.UnaryOperator<String> nameTransform = java.util.function.UnaryOperator.identity();

        TransactionalRowWriter(JdbcDataSource source, JdbcTemplate jdbc) { this.source = source; this.jdbc = jdbc; }

        @Transactional(rollbackFor = Exception.class)
        public EntityDataDTO save(EntityDataDTO dto, String trace) throws Exception {
            // 不经 Spring 获取独立连接，否则会读到当前事务未提交值，失去验证批次边界的意义。
            try (var connection = source.getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM imported_records")) {
                result.next(); observedCommittedCounts.add(result.getInt(1));
            }
            // 模拟正式表单处理返回与预览不同的最终值，测试跨已提交批次的方案规则终检。
            dto.setName(nameTransform.apply(dto.getName()));
            var values = new LinkedHashMap<>(dto.getData()); values.put("name", dto.getName()); dto.setData(values);
            attemptedNames.add(dto.getName());
            jdbc.update("INSERT INTO imported_records(id,name,memo) VALUES (?,?,?)", trace, dto.getName(), dto.getData().get("memo"));
            if (failNames.contains(dto.getName())) throw new IllegalArgumentException("写入被拒绝：" + dto.getName());
            return dto;
        }

        List<String> committedNames() { return jdbc.queryForList("SELECT name FROM imported_records", String.class); }
    }
}
