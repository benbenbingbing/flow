package com.workflow.entity.excel.runtime;

import static com.workflow.entity.excel.persistence.ExcelMapperTestSupport.mapper;
import com.workflow.entity.excel.infrastructure.persistence.mapper.EntityExcelJobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.*;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.excel.model.*;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.CurrentFormRuntimeContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用真实数据库事务验证跨多行写入与任务状态的原子性，不以Mockito调用次数代替回滚证据。 */
class EntityExcelJobTransactionTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final EntityExcelRuntimeResolver resolver = mock(EntityExcelRuntimeResolver.class);
    private final EntityExcelCodecService codec = mock(EntityExcelCodecService.class);
    private final EntityExcelFileStore files = mock(EntityExcelFileStore.class);
    private final EntityExcelAtomicImportPolicy policy = mock(EntityExcelAtomicImportPolicy.class);
    private final EntityDataActionService actions = mock(EntityDataActionService.class);
    private final EntityDataDynamicService data = mock(EntityDataDynamicService.class);
    private final EntityDataListConfigService lists = mock(EntityDataListConfigService.class);
    private final PublishedFormSubmissionService submissions = mock(PublishedFormSubmissionService.class);
    private final EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
    private final CurrentFormRuntimeContext formRuntime = mock(CurrentFormRuntimeContext.class);
    private JdbcTemplate jdbc;
    private EntityExcelJobRepository repository;
    private EntityExcelJobService service;
    private EntityExcelRuntimeResolver.Plan plan;
    private List<EntityExcelCodecService.Row> rows;

    @BeforeEach void setUp() throws Exception {
        UserContext.setCurrentUser("alice", "alice");
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:excel_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(database);
        Path migration = Path.of("../workflow-db-migrator/src/main/resources/db/migration/V115__entity_excel_configuration_and_jobs.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(database);
        jdbc.execute("CREATE TABLE imported_records(id VARCHAR(64) PRIMARY KEY, name VARCHAR(100))");
        var tx = new DataSourceTransactionManager(database);
        repository = spy(new EntityExcelJobRepository(mapper(jdbc.getDataSource(), EntityExcelJobMapper.class), mapper, tx));
        var origin = new UiEventExecuteRequest(); origin.setConfigType("LIST"); origin.setConfigId("list1");
        origin.setEntityCode("employee"); origin.setTargetKey("import"); origin.setInput(Map.of("filters", Map.of()));
        var claims = new ExcelActionTokenService.Claims(1, "alice", Instant.now().getEpochSecond() + 900, "BUILT_IN", origin,
                "source", "IMPORT", "SELECTED", "DEFAULT", "basic", "scheme", "default", "entity", "form", List.of("IMPORT", "CHECK", "TEMPLATE"));
        var document = new ExcelSchemeDocument(1, "basic", "员工", null,
                new ExcelSchemeDocument.FileConfig("数据", 1, "REJECT"),
                new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "ATOMIC_ALL", "create", false, 1000),
                new ExcelSchemeDocument.ExportConfig(true, "员工", "数据"), List.of());
        EntityForm form = new EntityForm(); form.setId("form1");
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(List.of());
        plan = new EntityExcelRuntimeResolver.Plan(claims,
                new ExcelResolvedScheme("entity1", "employee", "basic", document, "scheme", "default", "form1"), null,
                new EntityPublishedSnapshotService.PinnedEntitySnapshot(snapshot, "entity"), new ResolvedEntityFormRelease(form, "release", 1));
        when(resolver.resolve(eq("token"), anyString())).thenReturn(plan);
        when(resolver.originKey(plan)).thenReturn("list1:import"); when(resolver.filters(plan)).thenReturn(Map.of());
        rows = List.of(row(2, "张三"), row(5, "李四"));
        when(codec.parse(eq(plan), any())).thenReturn(new EntityExcelCodecService.Parsed(rows, List.of(), 2, 2, 2, 0, false, Map.of(0, "姓名")));
        when(files.put(any(), anyString(), anyString(), eq("alice"))).thenReturn("file1");
        when(files.metadata("file1")).thenReturn(Map.of("original_name", "错误报告.xlsx", "content_type", EntityExcelFileStore.XLSX));
        when(submissions.previewSideEffectFreeForm(any(), any(), any(), any(), isNull(), eq("create"), anyMap(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(6));
        when(definitions.findByEntityCodeForUpdate("employee")).thenReturn(Optional.of(new EntityDefinition()));
        when(formRuntime.graphHash(anyString())).thenReturn("form-hash");
        when(actions.createExcelRow(any(), anyString())).thenAnswer(invocation -> {
            EntityDataDTO dto = invocation.getArgument(0);
            jdbc.update("INSERT INTO imported_records(id,name) VALUES (?,?)", invocation.getArgument(1), dto.getName());
            return dto;
        });
        service = new EntityExcelJobService(resolver, codec, new ExcelActionTokenService(mapper, "test"), repository, files, policy,
                mock(EntityActionCapabilityService.class), actions, data, lists,
                submissions, mock(EntityDataMutationValidator.class), new EntityRuntimeRecordMapper(mapper), definitions,
                mock(EntityFormPublishedMapper.class), mock(UiConfigReleaseService.class), formRuntime, tx, mapper);
    }
    @AfterEach void clearUser() { UserContext.clear(); }

    @Test void defaultUploadLimitAcceptsFilesAbovePreviousFiveMbCap() throws Exception {
        var file = new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, new byte[6 * 1024 * 1024]);
        var result = service.importFile("token", "default-size", file, false);

        assertThat(result.get("status")).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isEqualTo(2);
    }

    @Test void defaultUploadLimitRejectsFilesAboveTenMbBeforeClaimingJob() {
        var file = new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.importFile("token", "too-large", file, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 MB");
        verify(repository, never()).claim(any(), anyString(), any(), anyString(), anyString());
        verifyNoInteractions(files);
    }

    @Test void configuredUploadLimitAcceptsExactBoundaryForCheckAndImport() throws Exception {
        uploadLimit(1);
        var file = new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, new byte[1024 * 1024]);

        assertThat(service.importFile("token", "boundary-check", file, true).get("status")).isEqualTo("VALIDATED");
        assertThat(service.importFile("token", "boundary-import", file, false).get("status")).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isEqualTo(2);
    }

    @Test void configuredUploadLimitRejectsOneExtraByteForCheckAndImport() {
        uploadLimit(1);
        var file = new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, new byte[1024 * 1024 + 1]);

        for (boolean checkOnly : List.of(true, false)) {
            assertThatThrownBy(() -> service.importFile("token", "oversize-" + checkOnly, file, checkOnly))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 MB");
        }
        verify(repository, never()).claim(any(), anyString(), any(), anyString(), anyString());
        verifyNoInteractions(files);
    }

    @Test void actualStreamCannotBypassConfiguredLimitWithForgedUploadSize() {
        uploadLimit(1);
        var file = new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, new byte[1024 * 1024 + 1]) {
            @Override public long getSize() { return 1; }
        };

        assertThatThrownBy(() -> service.importFile("token", "forged-size", file, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 MB");
        verify(repository, never()).claim(any(), anyString(), any(), anyString(), anyString());
        verifyNoInteractions(files);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isZero();
    }

    /** 配置真实执行计划，保持解析行与业务事务测试一致，仅改变上传受理的方案上限。 */
    private void uploadLimit(int maxFileSizeMb) {
        var old = plan.scheme();
        var doc = old.document();
        var options = doc.importConfig();
        var configured = new ExcelSchemeDocument.ImportConfig(options.enabled(), options.writePolicy(), options.transactionPolicy(),
                options.writeFormKey(), options.startProcess(), options.maxRows(), options.unknownColumnPolicy(),
                options.validateHeader(), options.stopAtEmptyRow(), options.batchSize(), options.returnUnimported(),
                options.precheckEnabled(), options.failurePolicy(), maxFileSizeMb);
        var scheme = new ExcelResolvedScheme(old.entityId(), old.entityCode(), old.schemeCode(),
                new ExcelSchemeDocument(doc.schemaVersion(), doc.schemeCode(), doc.name(), doc.description(), doc.file(),
                        configured, doc.exportConfig(), doc.columns()), old.contentHash(), old.defaultHash(), old.writeFormId());
        plan = new EntityExcelRuntimeResolver.Plan(plan.claims(), scheme, plan.list(), plan.entity(), plan.writeForm());
        when(resolver.resolve(eq("token"), anyString())).thenReturn(plan);
        when(resolver.originKey(plan)).thenReturn("list1:import");
        when(codec.parse(eq(plan), any())).thenReturn(new EntityExcelCodecService.Parsed(rows, List.of(), 2, 2, 2, 0, false, Map.of(0, "姓名")));
    }

    @Test void savesAllRowsWithDistinctTraceKeysAndReplayDoesNotInsertAgain() throws Exception {
        var result = service.importFile("token", "request1", file("same"), false);
        assertThat(result.get("status")).isEqualTo("SUCCEEDED");
        assertThat(((Number) result.get("savedRowCount")).intValue()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM imported_records", String.class))
                .anyMatch(id -> id.endsWith("_0_2")).anyMatch(id -> id.endsWith("_0_5"));
        var replay = service.importFile("token", "request1", file("same"), false);
        assertThat(replay.get("jobId")).isEqualTo(result.get("jobId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> service.importFile("token", "request1", file("changed"), false))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void failureOnSecondRowRollsBackFirstRowAndPersistsFailureAfterRollback() throws Exception {
        AtomicInteger count = new AtomicInteger();
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO imported_records(id,name) VALUES (?,?)", invocation.getArgument(1), "pending");
            if (count.incrementAndGet() == 2) throw new IllegalArgumentException("第二行业务校验失败");
            return invocation.getArgument(0);
        }).when(actions).createExcelRow(any(), anyString());
        var result = service.importFile("token", "rollback", file("same"), false);
        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(((Number) result.get("savedRowCount")).intValue()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT excel_row_number FROM entity_excel_job_row", Integer.class)).isEqualTo(5);
    }

    @Test void failureUpdatingSuccessStatusAlsoRollsBackBusinessRows() throws Exception {
        doThrow(new IllegalStateException("task update failed")).when(repository)
                .finish(anyString(), eq("SUCCEEDED"), anyMap(), isNull(), isNull(), isNull(), isNull());
        var result = service.importFile("token", "status-failure", file("same"), false);
        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isZero();
    }

    @Test void processedValuesFailingSchemeRulesRejectBeforeWriting() throws Exception {
        var issue = new EntityExcelCodecService.Error(0, "数据", 5, 0, "name", "姓名", "INVALID_VALUE", "处理后的姓名不符合方案规则");
        when(codec.validateProcessedRows(eq(plan), any(), anyList())).thenReturn(List.of(issue));
        var result = service.importFile("token", "processed-preview", file("same"), false);
        assertThat(result.get("status")).isEqualTo("REJECTED");
        verifyNoInteractions(actions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isZero();
        assertThat((List<Map<String, Object>>) result.get("errors")).singleElement()
                .satisfies(error -> assertThat(error.get("rowNumber")).isEqualTo(5));
    }

    @Test void authoritativeSavedValuesAreRecheckedAndRollbackAllRows() throws Exception {
        var issue = new EntityExcelCodecService.Error(0, "数据", 5, 0, "name", "姓名", "INVALID_VALUE", "正式处理后产生文件内重复值");
        when(codec.validateProcessedRows(eq(plan), any(), anyList())).thenReturn(List.of(), List.of(issue));
        var result = service.importFile("token", "processed-save", file("same"), false);
        assertThat(result.get("status")).isEqualTo("FAILED");
        verify(actions, times(2)).createExcelRow(any(), anyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isZero();
        assertThat(((Number) result.get("savedRowCount")).intValue()).isZero();
        assertThat((List<Map<String, Object>>) result.get("errors")).singleElement()
                .satisfies(error -> assertThat(error.get("columnLetter")).isEqualTo("A"));
    }

    @Test void checkOnlyNeverCallsBusinessWriterAndCannotReuseItsRequestIdForImport() throws Exception {
        var checked = service.importFile("token", "check-only", file("same"), true);
        assertThat(checked.get("status")).isEqualTo("VALIDATED");
        verifyNoInteractions(actions);
        assertThatThrownBy(() -> service.importFile("token", "check-only", file("same"), false))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void auditSnapshotKeepsConfigurationButDoesNotRetainFormInputOrSelectedRecords() throws Exception {
        plan.claims().origin().setContext(Map.of("formData", Map.of("private_name", "unsaved-sensitive-value")));
        plan.claims().origin().setSelectedIds(List.of("private-record-id"));
        service.importFile("token", "audit-minimal", file("same"), true);
        String retained = jdbc.queryForObject("SELECT config_json FROM entity_excel_job", String.class);
        assertThat(retained).doesNotContain("unsaved-sensitive-value", "private-record-id");
        assertThat(mapper.readTree(retained).path("claims").path("dataScope").asText()).isEqualTo("SELECTED");
        assertThat(mapper.readTree(retained).path("scheme").path("schemeCode").asText()).isEqualTo("basic");
    }

    @Test void rowValidationRejectsAllRowsAndErrorsKeepOriginalExcelCoordinates() throws Exception {
        var issue = new EntityExcelCodecService.Error(0, "数据", 5, 0, "name", "姓名", "REQUIRED", "姓名不能为空");
        when(codec.parse(eq(plan), any())).thenReturn(new EntityExcelCodecService.Parsed(rows, List.of(issue), 2, 2, 1, 1, false, Map.of(0, "姓名")));
        var result = service.importFile("token", "invalid", file("same"), false);
        assertThat(result.get("status")).isEqualTo("REJECTED");
        verifyNoInteractions(actions, submissions);
        assertThat(jdbc.queryForObject("SELECT excel_row_number FROM entity_excel_job_row", Integer.class)).isEqualTo(5);
        assertThat((List<Map<String, Object>>) result.get("errors")).singleElement()
                .satisfies(error -> assertThat(error.get("columnLetter")).isEqualTo("A"));
    }

    @Test void cleanupRemovesErrorsWithoutFilesAndTerminatesInterruptedJobs() throws Exception {
        var checked = service.importFile("token", "old-check", file("same"), true);
        String id = checked.get("jobId").toString();
        jdbc.update("UPDATE entity_excel_job SET status='RUNNING',created_at=?,input_file_id=NULL WHERE id=?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(25), id);
        var cleanup = new EntityExcelJobCleanup(mapper(jdbc.getDataSource(), EntityExcelJobMapper.class), files, new DataSourceTransactionManager(jdbc.getDataSource()));
        cleanup.clean();
        assertThat(jdbc.queryForObject("SELECT status FROM entity_excel_job WHERE id=?", String.class, id)).isEqualTo("FAILED");
        assertThatThrownBy(() -> repository.lockRunning(id)).isInstanceOf(IllegalStateException.class);
        repository.saveErrors(id, List.of(Map.of("sheetNo", 0, "rowNumber", 0, "message", "没有报告文件的错误")));
        cleanup.clean();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_excel_job_row WHERE job_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_excel_job WHERE id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test void cleanupDuringPreviewPreventsLateWriterAndKeepsExistingTerminalStatus() throws Exception {
        when(submissions.previewSideEffectFreeForm(any(), any(), any(), any(), isNull(), eq("create"), anyMap(), any(), any()))
                .thenAnswer(call -> {
                    jdbc.update("UPDATE entity_excel_job SET created_at=?", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(25));
                    new EntityExcelJobCleanup(mapper(jdbc.getDataSource(), EntityExcelJobMapper.class), files, new DataSourceTransactionManager(jdbc.getDataSource())).clean();
                    return call.getArgument(6);
                });
        var result = service.importFile("token", "expired-worker", file("same"), false);
        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(result.get("filesExpired")).isEqualTo(true);
        verifyNoInteractions(actions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_excel_job_row", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT error_message FROM entity_excel_job", String.class)).contains("超时或中断");
    }

    @Test void structuralFileErrorReturnsActionableReasonAndNeverSavesRows() throws Exception {
        doThrow(new com.alibaba.excel.exception.ExcelAnalysisException(new IllegalArgumentException("Excel 缺少必需列：姓名")))
                .when(codec).parse(eq(plan), any());
        var result = service.importFile("token", "bad-header", file("same"), false);
        assertThat(result.get("status")).isEqualTo("REJECTED");
        assertThat((List<Map<String, Object>>) result.get("errors")).singleElement().satisfies(error -> {
            assertThat(error.get("rowNumber")).isEqualTo(0);
            assertThat(error.get("message")).isEqualTo("Excel 缺少必需列：姓名");
        });
        verifyNoInteractions(actions, submissions);
    }

    @Test void longErrorMessageKeepsDownloadableReportAndFullPagedDetail() throws Exception {
        String message = "业务规则错误".repeat(7000);
        var issue = new EntityExcelCodecService.Error(0, "数据", 2, 0, "name", "姓名", "INVALID_VALUE", message);
        when(codec.parse(eq(plan), any())).thenReturn(new EntityExcelCodecService.Parsed(rows, List.of(issue), 2, 2, 1, 1, false, Map.of(0, "姓名")));
        List<byte[]> reports = new ArrayList<>();
        doAnswer(call -> {
            if (call.<String>getArgument(1).contains("错误报告")) reports.add(java.nio.file.Files.readAllBytes(call.getArgument(0)));
            return "file1";
        }).when(files).put(any(), anyString(), anyString(), eq("alice"));
        var result = service.importFile("token", "long-error", file("same"), false);
        assertThat(result.get("status")).isEqualTo("REJECTED");
        assertThat(reports).hasSize(1);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(reports.get(0)))) {
            String cell = workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue();
            assertThat(cell.length()).isLessThanOrEqualTo(32767);
            assertThat(cell).contains("已截断");
        }
        assertThat((List<Map<String, Object>>) result.get("errors")).singleElement()
                .satisfies(error -> assertThat(error.get("message")).isEqualTo(message));
    }

    @Test void templatePreservesColumnWidthsAndTextFormatBeforeUserEntersValues() throws Exception {
        workbookPlan("IMPORT");
        var realCodec = realCodec();
        when(codec.heads(plan, true)).thenReturn(realCodec.heads(plan, true));
        when(codec.importInstructions(plan)).thenReturn(realCodec.importInstructions(plan));
        var out = new java.io.ByteArrayOutputStream();
        service.template("token", out);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(out.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("姓名");
            assertThat(sheet.getColumnWidth(0)).isEqualTo(30 * 256);
            assertThat(sheet.getColumnStyle(0).getDataFormat()).isEqualTo((short) 49);
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(2).getStringCellValue()).contains("前导零");
            assertThat(workbook.getSheetAt(1).getRow(2).getCell(2).getStringCellValue()).contains("2位小数");
        }
    }

    @Test void formUnknownColumnPoliciesControlActualFileImportAndStoredValues() throws Exception {
        jdbc.execute("ALTER TABLE imported_records ADD COLUMN amount DECIMAL(12,2)");
        var realCodec = realCodec();
        when(codec.parse(any(), any())).thenAnswer(call -> realCodec.parse(call.getArgument(0), call.getArgument(1)));
        when(codec.validateProcessedRows(any(), any(), anyList())).thenAnswer(call ->
                realCodec.validateProcessedRows(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        doAnswer(call -> {
            EntityDataDTO dto = call.getArgument(0);
            jdbc.update("INSERT INTO imported_records(id,name,amount) VALUES (?,?,?)",
                    call.getArgument(1), dto.getName(), dto.getData().get("amount"));
            return dto;
        }).when(actions).createExcelRow(any(), anyString());
        var out = new java.io.ByteArrayOutputStream();
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = book.createSheet("数据");
            sheet.createRow(0).createCell(0).setCellValue("姓名");
            sheet.getRow(0).createCell(1).setCellValue("金额");
            sheet.createRow(1).createCell(0).setCellValue("张三");
            sheet.getRow(1).createCell(1).setCellValue("12.50");
            book.write(out);
        }
        for (String strategy : List.of("DIRECT", "IGNORE", "REJECT")) {
            workbookPlan("IMPORT");
            formUnknownPolicy(strategy, "name");
            clearInvocations(actions);
            var result = service.importFile("token", "unknown-" + strategy,
                    new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, out.toByteArray()), false);
            assertThat(result.get("status")).isEqualTo("REJECT".equals(strategy) ? "REJECTED" : "SUCCEEDED");
            if ("REJECT".equals(strategy)) {
                verifyNoInteractions(actions);
                assertThat(result.get("errors").toString()).contains("amount", "未知列");
            } else {
                verify(actions).createExcelRow(any(), anyString());
                Object amount = jdbc.queryForMap("SELECT amount FROM imported_records WHERE id LIKE ?",
                        "excel_" + result.get("jobId") + "%").get("amount");
                if ("DIRECT".equals(strategy)) assertThat((java.math.BigDecimal) amount).isEqualByComparingTo("12.50");
                else assertThat(amount).isNull();
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM imported_records", Integer.class)).isEqualTo(2);
    }

    @Test void ignoredFormColumnDoesNotShiftRemainingTemplateWidths() throws Exception {
        workbookPlan("IMPORT");
        formUnknownPolicy("IGNORE", "amount");
        var realCodec = realCodec();
        when(codec.heads(plan, true)).thenReturn(realCodec.heads(plan, true));
        when(codec.importInstructions(plan)).thenReturn(realCodec.importInstructions(plan));
        var out = new java.io.ByteArrayOutputStream();
        service.template("token", out);
        try (var book = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(out.toByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getPhysicalNumberOfCells()).isEqualTo(1);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("金额");
            assertThat(sheet.getColumnWidth(0)).isEqualTo(35 * 256);
            assertThat(sheet.getColumnStyle(0).getDataFormat()).isEqualTo((short) 49);
        }
    }

    /** 固定发布表单只声明一个字段，用真实 Excel 读写验证另一映射列的三种处理结果。 */
    private void formUnknownPolicy(String strategy, String formFieldCode) {
        var field = new com.workflow.entity.form.infrastructure.persistence.record.EntityFormField();
        field.setFieldCode(formFieldCode);
        plan.writeForm().form().setFields(List.of(field));
        var doc = plan.scheme().document();
        var config = doc.importConfig();
        var scheme = new ExcelResolvedScheme("entity1", "employee", "basic", new ExcelSchemeDocument(1, "basic", "员工", null,
                doc.file(), new ExcelSchemeDocument.ImportConfig(true, config.writePolicy(), config.transactionPolicy(),
                config.writeFormKey(), false, config.maxRows(), strategy), doc.exportConfig(), doc.columns()),
                "scheme", "default", "form1");
        plan = new EntityExcelRuntimeResolver.Plan(plan.claims(), scheme, null, plan.entity(), plan.writeForm());
        when(resolver.resolve(eq("token"), anyString())).thenReturn(plan);
        when(resolver.originKey(plan)).thenReturn("list1:workbook");
    }

    @Test void exportProducesRealXlsxWithConfiguredColumnsAndExactAmount() throws Exception {
        workbookPlan("EXPORT");
        when(resolver.withExportSelection(eq(plan), isNull(), isNull())).thenReturn(plan);
        when(codec.prepareExport(plan)).thenReturn(realCodec().prepareExport(plan));
        var dto = new EntityDataDTO(); dto.setName("=SUM(1,2)");
        dto.setData(Map.of("amount", new java.math.BigDecimal("9999999999999999.99")));
        when(data.findAccessibleById("employee", "record1", null)).thenReturn(dto);
        List<byte[]> generated = new ArrayList<>();
        doAnswer(call -> { generated.add(java.nio.file.Files.readAllBytes(call.getArgument(0))); return "file1"; })
                .when(files).put(any(), anyString(), anyString(), eq("alice"));
        var result = service.export("token", "export-real", null, null);
        assertThat(result.get("status")).isEqualTo("SUCCEEDED");
        assertThat(generated).hasSize(1);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(generated.get(0)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("=SUM(1,2)");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("9999999999999999.99");
            assertThat(sheet.getColumnWidth(0)).isEqualTo(30 * 256);
        }
    }

    private EntityExcelCodecService realCodec() {
        return new EntityExcelCodecService(mock(com.workflow.admin.dictionary.application.SysDictItemService.class),
                new com.workflow.entity.definition.application.EntityFieldValidationRuleService(mapper),
                mock(com.workflow.entity.definition.application.EntityStatusService.class));
    }

    private void workbookPlan(String operation) {
        var field = new com.workflow.entity.definition.infrastructure.persistence.record.EntityField();
        field.setFieldCode("name"); field.setFieldName("姓名"); field.setFieldType(com.workflow.entity.definition.infrastructure.persistence.record.EntityField.FieldType.STRING);
        var amount = new com.workflow.entity.definition.infrastructure.persistence.record.EntityField();
        amount.setFieldCode("amount"); amount.setFieldName("金额"); amount.setFieldType(com.workflow.entity.definition.infrastructure.persistence.record.EntityField.FieldType.DECIMAL);
        plan.entity().snapshot().setFields(List.of(field, amount));
        var document = plan.scheme().document();
        var columns = List.of(new ExcelSchemeDocument.Column("name", "姓名", List.of(), "name", true, true, true, 30,
                        Map.of("type", "STRING"), Map.of()),
                new ExcelSchemeDocument.Column("amount", "金额", List.of(), "amount", true, true, true, 35,
                        Map.of("type", "AMOUNT", "scale", 2), Map.of()));
        var scheme = new ExcelResolvedScheme("entity1", "employee", "basic", new ExcelSchemeDocument(1, "basic", "员工", null,
                document.file(), document.importConfig(), document.exportConfig(), columns), "scheme", "default", "form1");
        var old = plan.claims(); old.origin().setRecordId("record1");
        var claims = new ExcelActionTokenService.Claims(1, "alice", old.expiresAt(), old.entryType(), old.origin(), "source", operation,
                "CURRENT_RECORD", "DEFAULT", "basic", "scheme", "default", "entity", "form", List.of(operation));
        plan = new EntityExcelRuntimeResolver.Plan(claims, scheme, null, plan.entity(), plan.writeForm());
        when(resolver.resolve(eq("token"), anyString())).thenReturn(plan);
        when(resolver.originKey(plan)).thenReturn("list1:workbook"); when(resolver.filters(plan)).thenReturn(Map.of());
    }

    @Test void expiredAndCrossUserTasksCannotExposeFiles() throws Exception {
        var result = service.importFile("token", "expiry", file("same"), true);
        String id = result.get("jobId").toString();
        jdbc.update("UPDATE entity_excel_job SET created_at=? WHERE id=?", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(25), id);
        assertThat(service.get(id).get("filesExpired")).isEqualTo(true);
        assertThatThrownBy(() -> service.download(id, "ERROR_REPORT")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        UserContext.setCurrentUser("bob", "bob");
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(com.workflow.core.error.ForbiddenException.class);
    }

    private EntityExcelCodecService.Row row(int number, String name) {
        return new EntityExcelCodecService.Row(0, "数据", number, Map.of("name", name), Map.of(0, name));
    }
    private MockMultipartFile file(String content) { return new MockMultipartFile("file", "input.xlsx", EntityExcelFileStore.XLSX, content.getBytes()); }
}
