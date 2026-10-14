package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.EntityDataMutationValidator;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.application.model.EntityExportBatch;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.context.FormSubmissionExecutionContext;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.CurrentFormRuntimeContext;
import com.workflow.excel.ExcelUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界同步 Excel 任务。每行复用实体提交链路，新方案按配置分批提交并保留成功批次；
 * 历史 ATOMIC_ALL 方案仍整体提交。任务进度与相应业务批次在同一事务更新。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityExcelJobService {
    private static final long MAX_EXPORT_ROWS = 100_000;
    private static final int MAX_ERRORS = 10_000;
    private final EntityExcelRuntimeResolver resolver;
    private final EntityExcelCodecService codec;
    private final ExcelActionTokenService tokens;
    private final EntityExcelJobRepository jobs;
    private final EntityExcelFileStore files;
    private final EntityExcelAtomicImportPolicy atomicPolicy;
    private final EntityActionCapabilityService permissions;
    private final EntityDataActionService actions;
    private final EntityDataDynamicService data;
    private final EntityDataListConfigService lists;
    private final PublishedFormSubmissionService submissions;
    private final EntityDataMutationValidator validator;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityDefinitionMapper definitions;
    private final EntityFormPublishedMapper formPublication;
    private final UiConfigReleaseService releases;
    private final CurrentFormRuntimeContext formRuntime;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper mapper;

    /** 执行只校验或校验后直接保存；同requestId重试只返回已认领的任务。 */
    public Map<String, Object> importFile(String actionToken, String requestId, MultipartFile upload, boolean checkOnly) throws IOException {
        String operation = checkOnly ? "CHECK" : "IMPORT";
        var plan = resolver.resolve(actionToken, operation);
        if (checkOnly && !plan.scheme().document().importConfig().precheckEnabled())
            throw new IllegalArgumentException("当前 Excel 方案未启用预校验");
        int maxFileSizeMb = plan.scheme().document().importConfig().maxFileSizeMb();
        long maxFileSize = maxFileSizeMb * 1024L * 1024L;
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("请选择非空的 Excel 文件");
        if (upload.getSize() > maxFileSize) throw new IllegalArgumentException(fileSizeMessage(maxFileSizeMb));
        String filename = EntityExcelRuntimeResolver.safeName(upload.getOriginalFilename());
        if (!filename.toLowerCase(java.util.Locale.ROOT).matches(".*\\.xlsx?"))
            throw new IllegalArgumentException("仅支持XLS或XLSX文件");
        Path input = Files.createTempFile("workflow-excel-input-", ".tmp");
        try {
            try (InputStream in = upload.getInputStream(); OutputStream out = Files.newOutputStream(input)) {
                copyBounded(in, out, maxFileSizeMb);
            }
            var claim = jobs.claim(plan, operation, resolver.originKey(plan), requestId, payloadHash(plan, operation, digest(input)));
            if (!claim.acquired()) return get(claim.jobId());
            String jobId = claim.jobId();
            EntityExcelCodecService.Parsed parsed = null;
            List<Map<String, Object>> errors = new ArrayList<>();
            String failureStatus = null;
            String failureMessage = null;
            try {
                String inputId = files.put(input, filename, filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xls")
                        ? "application/vnd.ms-excel" : EntityExcelFileStore.XLSX, UserContext.getUserId());
                try { jobs.inputFile(jobId, inputId); }
                catch (RuntimeException failure) { deleteUnlinkedFile(inputId); throw failure; }
                String formConfigHash = formConfigHash(plan);
                atomicPolicy.requireSupported(plan, formConfigHash);
                try (InputStream in = Files.newInputStream(input)) { parsed = codec.parse(plan, in); }
                catch (com.alibaba.excel.exception.ExcelAnalysisException | IllegalArgumentException invalid) {
                    throw new FileValidationException(safeMessage(invalid));
                }
                if ("BATCH".equals(plan.scheme().document().importConfig().transactionPolicy())) {
                    importBatches(actionToken, plan, jobId, parsed, formConfigHash, input, checkOnly);
                    return get(jobId);
                }
                appendCodecErrors(errors, parsed.errors());
                if (parsed.rows().isEmpty()) appendError(errors, error(null, "EMPTY_FILE", "文件中没有可导入的数据"));
                if (errors.isEmpty() && !parsed.errorsTruncated()) preview(plan, jobId, parsed, errors);
                if (!errors.isEmpty() || parsed.errorsTruncated()) {
                    failureStatus = "REJECTED";
                    failureMessage = "文件校验未通过，未保存任何记录";
                } else if (checkOnly) {
                    jobs.finish(jobId, "VALIDATED", counters(parsed, errors, 0), null, null, null, null);
                } else {
                    saveAtomically(actionToken, plan, jobId, parsed, formConfigHash);
                }
            } catch (FileValidationException ex) {
                appendError(errors, error(null, "FILE_VALIDATION", ex.getMessage()));
                failureStatus = "REJECTED";
                failureMessage = "文件校验未通过，未保存任何记录";
            } catch (ProcessedValidationException ex) {
                appendCodecErrors(errors, ex.errors);
                failureStatus = "FAILED";
                failureMessage = "保存结果校验失败，全部记录已回滚";
            } catch (RowSaveException ex) {
                appendError(errors, error(ex.row, "SAVE_FAILED", safeMessage(ex.getCause())));
                failureStatus = "FAILED";
                failureMessage = "保存失败，全部记录已回滚";
            } catch (Exception ex) {
                log.warn("Excel任务失败: jobId={}, failureType={}", jobId, ex.getClass().getSimpleName());
                appendError(errors, error(null, "IMPORT_FAILED", safeMessage(ex)));
                failureStatus = "FAILED";
                failureMessage = "导入未完成，未保存任何记录";
            }
            // 失败收尾在业务事务已回滚后仅执行一次；收尾自身故障交由任务重查/到期清理处理。
            if (failureStatus != null) {
                if ("BATCH".equals(plan.scheme().document().importConfig().transactionPolicy())) {
                    // 分批流程意外中断时读取已提交的进度，不能用旧原子分支的零成功覆盖真实结果。
                    var progress = jobs.readMap((String) jobs.requireOwned(jobId).get("counters_json"));
                    long saved = ((Number) progress.getOrDefault("savedRowCount", 0)).longValue();
                    Set<Integer> savedRows = new LinkedHashSet<>();
                    if (progress.get("savedExcelRows") instanceof List<?> rows)
                        rows.forEach(row -> savedRows.add(((Number) row).intValue()));
                    if (parsed == null) parsed = readRejectedRows(plan, input);
                    finishBatchJob(plan, jobId, parsed, input, errors, savedRows, savedRows, checkOnly,
                            saved > 0 ? "PARTIAL_SUCCESS" : failureStatus, saved,
                            "导入已中断，已提交记录保留，请核对实际新增数量");
                } else reject(jobId, parsed, errors, failureStatus, failureMessage);
            }
            return get(jobId);
        } finally { Files.deleteIfExists(input); }
    }

    /** 预检不执行远程写入，预览与正式保存使用不同追踪键，避免缓存预览替代实际处理。 */
    private void preview(EntityExcelRuntimeResolver.Plan plan, String jobId, EntityExcelCodecService.Parsed parsed,
            List<Map<String, Object>> errors) {
        preview(plan, jobId, parsed, errors, false);
    }

    private void preview(EntityExcelRuntimeResolver.Plan plan, String jobId, EntityExcelCodecService.Parsed parsed,
            List<Map<String, Object>> errors, boolean stopOnError) {
        try (var ignored = ExcelAtomicExecutionContext.open()) {
            List<Map<String, Object>> storageRows = new ArrayList<>();
            for (var row : parsed.rows()) {
                try {
                    var context = new FormSubmissionExecutionContext(trace(jobId, row) + "_check", "EXCEL_CHECK", Map.of());
                    var values = submissions.previewSideEffectFreeForm(plan.writeForm().form().getId(), plan.writeForm().releaseId(),
                            plan.writeForm().releaseVersion(), plan.scheme().entityCode(), null, "create", row.data(), context,
                            UiRuntimeResolutionContext.standalone());
                    storageRows.add(recordMapper.toStorageMap(dto(plan, values, formConfigHash(plan))));
                } catch (RuntimeException ex) {
                    storageRows.add(null);
                    appendError(errors, error(row, "FORM_VALIDATION", safeMessage(ex)));
                    if (stopOnError) break;
                }
            }
            while (storageRows.size() < parsed.rows().size()) storageRows.add(null);
            appendCodecErrors(errors, codec.validateProcessedRows(plan, parsed, storageRows));
            // 在最终默认值/纯表单处理结果上批量查唯一性，避免按单元格查库，同时保留原文件坐标。
            for (var failure : validator.validatePublishedFieldsBatch(plan.scheme().entityCode(), storageRows)) {
                var row = failure.rowIndex() < 0 ? null : parsed.rows().get(failure.rowIndex());
                Map<String, Object> issue = new LinkedHashMap<>(error(row, failure.code(), failure.message()));
                if (failure.fieldCode() != null) {
                    issue.put("fieldCode", failure.fieldCode());
                    var column = plan.scheme().document().columns().stream()
                            .filter(value -> failure.fieldCode().equals(value.fieldCode())).findFirst().orElse(null);
                    if (column != null) {
                        issue.put("header", column.header());
                        var options = plan.scheme().document().importConfig();
                        if ("BATCH".equals(options.transactionPolicy()) && !options.validateHeader()) {
                            int index = ExcelImportFormColumnPolicy.resolve(plan).importColumns().indexOf(column);
                            if (index >= 0) {
                                issue.put("columnIndex", index);
                                issue.put("columnLetter", org.apache.poi.ss.util.CellReference.convertNumToColString(index));
                            }
                        } else parsed.headers().entrySet().stream().filter(entry -> column.header().equals(entry.getValue())
                                    || !"BATCH".equals(options.transactionPolicy()) && column.headerAliases().contains(entry.getValue()))
                                .findFirst().ifPresent(entry -> {
                                    issue.put("columnIndex", entry.getKey());
                                    issue.put("columnLetter", org.apache.poi.ss.util.CellReference.convertNumToColString(entry.getKey()));
                                });
                    }
                }
                appendError(errors, issue);
            }
        }
    }

    private void saveAtomically(String token, EntityExcelRuntimeResolver.Plan plan, String jobId,
            EntityExcelCodecService.Parsed parsed, String formConfigHash) {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> {
            jobs.lockRunning(jobId);
            // 与方案、实体、表单发布使用同一锁顺序。批次内不能切换表结构、默认值或提交规则。
            formPublication.lockPublication();
            definitions.findByEntityCodeForUpdate(plan.scheme().entityCode()).orElseThrow(() -> new IllegalArgumentException("实体已删除"));
            releases.lockFormForProcessPublish(plan.writeForm().form().getId());
            resolver.resolve(token, "IMPORT");
            atomicPolicy.requireSupported(plan, formConfigHash);
            List<Map<String, Object>> savedRows = new ArrayList<>();
            for (var row : parsed.rows()) {
                try { savedRows.add(recordMapper.toStorageMap(actions.createExcelRow(dto(plan, row.data(), formConfigHash), trace(jobId, row)))); }
                catch (RuntimeException ex) { throw new RowSaveException(row, ex); }
            }
            // 正式提交可再次执行只读前置处理，结果可能不同于预览；提交前复核权威返回值，失败回滚整个批次。
            var finalErrors = codec.validateProcessedRows(plan, parsed, savedRows);
            if (!finalErrors.isEmpty()) throw new ProcessedValidationException(finalErrors);
            jobs.finish(jobId, "SUCCEEDED", counters(parsed, List.of(), parsed.rows().size()), null, null, null, null);
        });
    }

    /**
     * 分批读取候选并提交。错误批次回滚后剔除失败行重试；STOP 只重试失败行之前的前缀。
     * 不能在 rollback-only 的事务中 catch 后继续，也不能每行独立提交冒充“提交条数”。
     */
    private void importBatches(String token, EntityExcelRuntimeResolver.Plan plan, String jobId,
            EntityExcelCodecService.Parsed parsed, String formHash, Path input, boolean checkOnly) throws IOException {
        var options = plan.scheme().document().importConfig();
        boolean stop = "STOP".equals(options.failurePolicy());
        List<Map<String, Object>> errors = new ArrayList<>();
        appendCodecErrors(errors, parsed.errors());
        // 超出错误预算的行仍可能含转换失败值，不能仅凭可展示明细来判定这些行有效。
        if (parsed.errorsTruncated()) appendError(errors, error(null, "ERROR_LIMIT", "错误明细过多，已停止导入，请修正数据后重试"));
        Set<Integer> accepted = new LinkedHashSet<>();
        Map<Integer, Map<String, Object>> committed = new LinkedHashMap<>();
        if (parsed.allRows().isEmpty()) appendError(errors, error(null, "EMPTY_FILE", "文件中没有可导入的数据"));
        try {
            if (!hasFileError(errors)) {
                // 预校验不提交事务，合并全部候选检查跨批重复值，不能因提交条数拆散文件级唯一校验。
                int chunkSize = checkOnly ? Math.max(1, parsed.rows().size()) : options.batchSize();
                for (int offset = 0; offset < parsed.rows().size(); offset += chunkSize) {
                    var chunk = parsed.rows().subList(offset, Math.min(offset + chunkSize, parsed.rows().size()));
                    List<EntityExcelCodecService.Row> eligible = eligibleRows(chunk, errors, stop);
                    List<Map<String, Object>> previewErrors = new ArrayList<>();
                    if (!eligible.isEmpty()) preview(plan, jobId, parsed.subset(eligible), previewErrors, stop);
                    previewErrors.forEach(issue -> appendError(errors, issue));
                    eligible = eligibleRows(eligible, errors, stop);
                    if (checkOnly) eligible.forEach(row -> accepted.add(row.rowNumber()));
                    else saveRecoverableBatch(token, plan, jobId, parsed, eligible, formHash, stop, errors, committed);
                    if (hasFileError(errors) || stop && firstFailure(errors) <= chunk.get(chunk.size() - 1).rowNumber()) break;
                }
            }
        } catch (Exception failure) {
            log.warn("Excel分批任务中断: jobId={}, failureType={}", jobId, failure.getClass().getSimpleName());
            appendError(errors, error(null, "IMPORT_FAILED", safeMessage(failure)));
        }
        if (!checkOnly) accepted.addAll(committed.keySet());
        // 停止模式只报告实际到达的第一个错误；解析器可能比表单/保存先看到后面的错误。
        if (stop) {
            int first = firstFailure(errors);
            errors.removeIf(issue -> rowNumber(issue) > first);
        }
        long notImported = parsed.allRows().stream().filter(row -> !accepted.contains(row.rowNumber())).count();
        String status = checkOnly ? errors.isEmpty() ? "VALIDATED" : "REJECTED"
                : errors.isEmpty() && notImported == 0 ? "SUCCEEDED"
                : !committed.isEmpty() ? "PARTIAL_SUCCESS" : "REJECTED";
        String message = checkOnly ? errors.isEmpty() ? "预校验通过，尚未保存数据" : "预校验未通过，尚未保存数据"
                : errors.isEmpty() && notImported == 0 ? null
                : "已新增 " + committed.size() + " 条，未导入 " + notImported + " 条；已提交记录保留";
        finishBatchJob(plan, jobId, parsed, input, errors, accepted, committed.keySet(), checkOnly,
                status, committed.size(), message);
    }

    /** 过滤失败行；停止模式保留首个失败之前的有效前缀，继续模式保留全部有效行。 */
    private List<EntityExcelCodecService.Row> eligibleRows(List<EntityExcelCodecService.Row> rows,
            List<Map<String, Object>> errors, boolean stop) {
        if (hasFileError(errors)) return List.of();
        int first = stop ? firstFailure(errors) : Integer.MAX_VALUE;
        Set<Integer> invalid = errors.stream().map(EntityExcelJobService::rowNumber).collect(java.util.stream.Collectors.toSet());
        return rows.stream().filter(row -> row.rowNumber() < first && !invalid.contains(row.rowNumber())).toList();
    }

    /** 已提交行只用于跨批最终唯一性校验，绝不重新新增。每次重试都开启新的事务和 DTO。 */
    private void saveRecoverableBatch(String token, EntityExcelRuntimeResolver.Plan plan, String jobId,
            EntityExcelCodecService.Parsed parsed, List<EntityExcelCodecService.Row> initial, String formHash,
            boolean stop, List<Map<String, Object>> errors, Map<Integer, Map<String, Object>> committed) {
        List<EntityExcelCodecService.Row> pending = initial;
        while (!pending.isEmpty()) {
            List<EntityExcelCodecService.Row> batch = pending;
            try {
                var transaction = new TransactionTemplate(transactions);
                transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                Map<Integer, Map<String, Object>> result = transaction.execute(state -> {
                    lockImportBatch(token, plan, jobId, formHash);
                    Map<Integer, Map<String, Object>> saved = new LinkedHashMap<>();
                    for (var row : batch) {
                        try { saved.put(row.rowNumber(), recordMapper.toStorageMap(
                                actions.createExcelRow(dto(plan, row.data(), formHash), trace(jobId, row)))); }
                        catch (RuntimeException failure) { throw new RowSaveException(row, failure); }
                    }
                    List<EntityExcelCodecService.Row> checked = parsed.rows().stream()
                            .filter(row -> committed.containsKey(row.rowNumber()) || saved.containsKey(row.rowNumber())).toList();
                    List<Map<String, Object>> values = checked.stream().map(row -> saved.containsKey(row.rowNumber())
                            ? saved.get(row.rowNumber()) : committed.get(row.rowNumber())).toList();
                    var issues = codec.validateProcessedRows(plan, parsed.subset(checked), values).stream()
                            .filter(issue -> issue.rowNumber() == 0 || saved.containsKey(issue.rowNumber())).toList();
                    if (!issues.isEmpty()) throw new ProcessedValidationException(issues);
                    Set<Integer> allSaved = new LinkedHashSet<>(committed.keySet()); allSaved.addAll(saved.keySet());
                    var progress = batchCounters(parsed, errors, allSaved, allSaved, false, allSaved.size());
                    progress.put("savedExcelRows", List.copyOf(allSaved));
                    jobs.progress(jobId, progress);
                    return saved;
                });
                committed.putAll(Objects.requireNonNull(result));
                return;
            } catch (RowSaveException failure) {
                appendError(errors, error(failure.row, "SAVE_FAILED", safeMessage(failure.getCause())));
            } catch (ProcessedValidationException failure) {
                appendCodecErrors(errors, failure.errors);
            }
            // 捕获发生在事务模板之外，原批次已经完全回滚。每次至少移除一个失败行，重试次数有界。
            List<EntityExcelCodecService.Row> retry = eligibleRows(batch, errors, stop);
            if (retry.size() >= batch.size()) throw new IllegalStateException("无法定位失败数据行，已停止当前批次");
            pending = retry;
        }
    }

    /** 同一锁顺序固定任务、表结构和表单，批次之间配置变化会阻止后续写入。 */
    private void lockImportBatch(String token, EntityExcelRuntimeResolver.Plan plan, String jobId, String formHash) {
        jobs.lockRunning(jobId);
        formPublication.lockPublication();
        definitions.findByEntityCodeForUpdate(plan.scheme().entityCode()).orElseThrow(() -> new IllegalArgumentException("实体已删除"));
        releases.lockFormForProcessPublish(plan.writeForm().form().getId());
        resolver.resolve(token, "IMPORT");
        atomicPolicy.requireSupported(plan, formHash);
    }

    /** 汇总每行未导入原因并结束任务；报告开关只控制原始数据文件，错误摘要始终可见。 */
    private void finishBatchJob(EntityExcelRuntimeResolver.Plan plan, String id, EntityExcelCodecService.Parsed parsed,
            Path input, List<Map<String, Object>> errors, Set<Integer> accepted, Set<Integer> saved, boolean checkOnly,
            String status, long savedCount, String message) throws IOException {
        Map<Integer, String> reasons = new LinkedHashMap<>();
        if (parsed != null) for (var row : parsed.allRows()) {
            if (accepted.contains(row.rowNumber())) continue;
            List<String> details = errors.stream().filter(issue -> rowNumber(issue) == row.rowNumber() || rowNumber(issue) == 0)
                    .map(issue -> String.valueOf(issue.get("message"))).distinct().toList();
            String reason = !details.isEmpty() ? String.join("；", details)
                    : parsed.unimportedReasons().getOrDefault(row.rowNumber(), "前序行失败，已停止导入");
            reasons.put(row.rowNumber(), reason);
        }
        String report = null;
        if (plan.scheme().document().importConfig().returnUnimported() && !reasons.isEmpty()) {
            try { report = unimportedReport(input, plan.scheme().document().file().sheetName(), reasons); }
            catch (Exception failure) { log.warn("Excel未导入数据报告生成失败: jobId={}, failureType={}", id, failure.getClass().getSimpleName()); }
        }
        String reportId = report;
        var counters = batchCounters(parsed, errors, accepted, saved, checkOnly, savedCount);
        // 具体未执行行也可分页查看；统计失败和未处理分开，避免把空行截止后的记录误算成校验错误。
        if (parsed != null) for (var row : parsed.allRows()) {
            if (reasons.containsKey(row.rowNumber()) && errors.stream().noneMatch(issue -> rowNumber(issue) == row.rowNumber()))
                appendError(errors, error(row, "NOT_IMPORTED", reasons.get(row.rowNumber())));
        }
        counters.put("errorCount", errors.size());
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            boolean updated = Boolean.TRUE.equals(transaction.execute(state -> {
                if (!jobs.lockIfRunning(id)) return false;
                jobs.saveErrors(id, errors);
                jobs.finish(id, status, counters, null, null, reportId, message);
                return true;
            }));
            if (!updated && reportId != null) deleteUnlinkedFile(reportId);
        } catch (RuntimeException failure) {
            if (reportId != null) deleteUnlinkedFile(reportId);
            throw failure;
        }
    }

    /** 成功、规则失败和未处理行分别计数，预校验的通过数不能混入实际新增数。 */
    private Map<String, Object> batchCounters(EntityExcelCodecService.Parsed parsed, List<Map<String, Object>> errors,
            Set<Integer> accepted, Set<Integer> saved, boolean checkOnly, long savedCount) {
        long total = parsed == null ? 0 : parsed.dataRowCount();
        long invalid = errors.stream().filter(issue -> rowNumber(issue) > 0 && !"NOT_IMPORTED".equals(issue.get("errorCode")))
                .map(EntityExcelJobService::rowNumber).distinct().count();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("dataRowCount", total); counts.put("validRowCount", accepted.size());
        counts.put("invalidRowCount", invalid); counts.put("savedRowCount", savedCount);
        counts.put("skippedRowCount", parsed == null ? 0 : parsed.skippedRowCount());
        counts.put("unimportedRowCount", Math.max(0, total - (checkOnly ? accepted.size() : savedCount)));
        counts.put("stoppedRowCount", Math.max(0, total - accepted.size() - invalid));
        counts.put("errorCount", errors.size()); counts.put("errorsTruncated", parsed != null && parsed.errorsTruncated());
        return counts;
    }

    private static int rowNumber(Map<String, Object> issue) {
        return ((Number) issue.getOrDefault("rowNumber", 0)).intValue();
    }
    private static int firstFailure(List<Map<String, Object>> errors) {
        return errors.stream().mapToInt(EntityExcelJobService::rowNumber).min().orElse(Integer.MAX_VALUE);
    }
    private static boolean hasFileError(List<Map<String, Object>> errors) {
        return errors.stream().anyMatch(issue -> rowNumber(issue) == 0);
    }

    /**
     * 从原始文件复制未导入整行，保留字符串、数字、日期显示格式及未映射列；仅附加行数和原因。
     * 公式以等号开头的文本返回，报告不会执行上传者公式；输出仍是可重新整理的 XLSX。
     */
    private String unimportedReport(Path input, String sheetName, Map<Integer, String> reasons) throws IOException {
        Path path = Files.createTempFile("workflow-excel-unimported-", ".xlsx");
        try (var source = org.apache.poi.ss.usermodel.WorkbookFactory.create(input.toFile());
                var output = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            // 复制原始日期序列值时必须一并保留日期系统，避免 1904 工作簿在报告中偏移 1462 天。
            boolean date1904 = source instanceof org.apache.poi.xssf.usermodel.XSSFWorkbook xlsx ? xlsx.isDate1904()
                    : source instanceof org.apache.poi.hssf.usermodel.HSSFWorkbook xls && xls.getInternalWorkbook().isUsing1904DateWindowing();
            output.getCTWorkbook().getWorkbookPr().setDate1904(date1904);
            var sheet = source.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("导入 Sheet 不存在");
            int width = 0;
            for (var row : sheet) width = Math.max(width, row.getLastCellNum());
            var target = output.createSheet("未导入数据");
            Map<Short, org.apache.poi.ss.usermodel.CellStyle> formats = new LinkedHashMap<>();
            copyReportRow(sheet.getRow(0), target.createRow(0), width, formats, output);
            target.getRow(0).createCell(width).setCellValue("行数");
            target.getRow(0).createCell(width + 1).setCellValue("原因");
            for (int column = 0; column < width; column++) target.setColumnWidth(column, sheet.getColumnWidth(column));
            target.setColumnWidth(width, 12 * 256); target.setColumnWidth(width + 1, 60 * 256);
            int index = 1;
            for (var entry : reasons.entrySet()) {
                var row = target.createRow(index++);
                copyReportRow(sheet.getRow(entry.getKey() - 1), row, width, formats, output);
                row.createCell(width).setCellValue(entry.getKey());
                row.createCell(width + 1).setCellValue(reportText(entry.getValue()));
            }
            try (OutputStream stream = Files.newOutputStream(path)) { output.write(stream); }
            return files.put(path, "Excel未导入数据.xlsx", EntityExcelFileStore.XLSX, UserContext.getUserId());
        } finally { Files.deleteIfExists(path); }
    }

    /**
     * 表单/未知列等前置规则拒绝时仍可报告可读取的原始文件。此处只收集原始行，绝不转换或保存。
     * 损坏文件、缺少 Sheet 或超出既有行列资源限制时无法可靠还原，保留原文件级错误摘要。
     */
    private EntityExcelCodecService.Parsed readRejectedRows(EntityExcelRuntimeResolver.Plan plan, Path input) {
        List<EntityExcelCodecService.Row> rows = new ArrayList<>();
        Map<Integer, String> headers = new LinkedHashMap<>();
        try (InputStream stream = Files.newInputStream(input)) {
            ExcelUtils.readRawRows(stream, plan.scheme().document().file().sheetName(),
                    new com.workflow.excel.ExcelReadOptions(0, 1, 200, false),
                    plan.scheme().document().importConfig().maxRows(), head -> {
                        head.cells().forEach((index, cell) -> headers.put(index, rawReportValue(cell)));
                    }, batch -> {
                        for (var row : batch) {
                            if (row.isBlank()) continue;
                            Map<Integer, String> raw = new LinkedHashMap<>();
                            row.cells().forEach((index, cell) -> {
                                if (index >= 200) throw new IllegalArgumentException("原始文件超过列数限制");
                                raw.put(index, rawReportValue(cell));
                            });
                            rows.add(new EntityExcelCodecService.Row(row.sheetNo(), row.sheetName(), row.rowNumber(), Map.of(), raw));
                        }
                    });
            return new EntityExcelCodecService.Parsed(List.of(), List.of(), rows.size(), 0, 0, 0, false,
                    headers, rows, Map.of());
        } catch (Exception unreadable) { return null; }
    }

    private static String rawReportValue(com.workflow.excel.dynamic.ExcelRawCell cell) {
        return cell.type() == com.workflow.excel.dynamic.ExcelCellType.FORMULA
                ? "=" + Objects.toString(cell.value(), "") : Objects.toString(cell.value(), "");
    }

    private void copyReportRow(org.apache.poi.ss.usermodel.Row source, org.apache.poi.ss.usermodel.Row target, int width,
            Map<Short, org.apache.poi.ss.usermodel.CellStyle> formats, org.apache.poi.ss.usermodel.Workbook output) {
        if (source == null) return;
        target.setHeight(source.getHeight());
        for (int index = 0; index < width; index++) {
            var original = source.getCell(index);
            if (original == null) continue;
            var cell = target.createCell(index);
            switch (original.getCellType()) {
                case STRING -> cell.setCellValue(original.getStringCellValue());
                case NUMERIC -> cell.setCellValue(original.getNumericCellValue());
                case BOOLEAN -> cell.setCellValue(original.getBooleanCellValue());
                case FORMULA -> cell.setCellValue("=" + original.getCellFormula());
                case ERROR -> cell.setCellErrorValue(original.getErrorCellValue());
                default -> cell.setBlank();
            }
            // XLS 与 XLSX 样式不能直接 clone，迁移显示格式可同时保留日期及前导零的可见值。
            var style = formats.computeIfAbsent(original.getCellStyle().getIndex(), key -> {
                var format = output.createCellStyle();
                format.setDataFormat(output.createDataFormat().getFormat(original.getCellStyle().getDataFormatString()));
                return format;
            });
            cell.setCellStyle(style);
        }
    }

    private EntityDataDTO dto(EntityExcelRuntimeResolver.Plan plan, Map<String, Object> values, String formConfigHash) {
        var dto = new EntityDataDTO();
        dto.setEntityCode(plan.scheme().entityCode()); dto.setData(new LinkedHashMap<>(values));
        dto.setName(values.get("name") == null ? null : String.valueOf(values.get("name")));
        dto.setCode(values.get("code") == null ? null : String.valueOf(values.get("code")));
        dto.setFormId(plan.writeForm().form().getId()); dto.setFormConfigHash(formConfigHash);
        dto.setRuntimeContext(Map.of());
        dto.setStartProcess(false);
        return dto;
    }

    private String formConfigHash(EntityExcelRuntimeResolver.Plan plan) {
        if (plan.writeForm() == null) throw new IllegalArgumentException("导入方案未选择写入表单");
        return formRuntime.graphHash(plan.writeForm().form().getId());
    }

    /** 以游标惰性读取并写入临时文件；完整成功后才发布可下载文件。 */
    public Map<String, Object> export(String token, String requestId, List<String> recordIds, Map<String, Object> filters) throws IOException {
        var prepared = resolver.resolve(token, "EXPORT");
        var plan = resolver.withExportSelection(prepared, recordIds, filters);
        // 点击时的选择集及条件已签名；提交不能趁机更换成另一个数据范围。
        if (!EntityExcelRuntimeResolver.hasSelectionDialog(plan.claims()) && recordIds != null && !recordIds.isEmpty() && !Objects.equals(recordIds, plan.claims().origin().getSelectedIds()))
            throw new IllegalArgumentException("导出选择已变化，请重新点击导出按钮");
        if (!EntityExcelRuntimeResolver.hasSelectionDialog(plan.claims()) && filters != null && !filters.isEmpty() && !Objects.equals(tokens.hash(filters), tokens.hash(plan.claims().origin().getInput().get("filters"))))
            throw new IllegalArgumentException("导出筛选已变化，请重新点击导出按钮");
        var claim = jobs.claim(plan, "EXPORT", resolver.originKey(plan), requestId, payloadHash(plan, "EXPORT", ""));
        if (!claim.acquired()) return get(claim.jobId());
        String jobId = claim.jobId();
        Path output = Files.createTempFile("workflow-excel-output-", ".xlsx");
        String fileId = null;
        try {
            var formatter = codec.prepareExport(plan);
            AtomicLong count = new AtomicLong();
            Iterable<Collection<? extends List<?>>> batches = exportBatches(plan, formatter, count);
            try (OutputStream out = Files.newOutputStream(output)) {
                ExcelUtils.writeRowsBatches(out, formatter.heads(), batches, plan.scheme().document().exportConfig().sheetName(),
                        sheetStyle(plan, false));
            }
            String scope = plan.claims().dataScope();
            if ("SELECTED".equals(scope) && count.get() != plan.claims().origin().getSelectedIds().stream().distinct().count())
                throw new IllegalArgumentException("部分选中记录不存在或已不在可导出范围，请刷新列表");
            String name = exportName(plan);
            fileId = files.put(output, name, EntityExcelFileStore.XLSX, UserContext.getUserId());
            jobs.finish(jobId, "SUCCEEDED", Map.of("dataRowCount", count.get(), "exportedRowCount", count.get()), fileId, name, null, null);
        } catch (Exception ex) {
            if (fileId != null) deleteUnlinkedFile(fileId);
            reject(jobId, null, List.of(error(null, "EXPORT_FAILED", safeMessage(ex))), "FAILED", "导出失败，未生成可下载文件");
        } finally { Files.deleteIfExists(output); }
        return get(jobId);
    }

    private Iterable<Collection<? extends List<?>>> exportBatches(EntityExcelRuntimeResolver.Plan plan,
            EntityExcelCodecService.ExportFormatter formatter, AtomicLong count) {
        return () -> new Iterator<>() {
            private EntityExportBatch.Cursor cursor;
            private boolean finished;
            @Override public boolean hasNext() { return !finished; }
            @Override public Collection<? extends List<?>> next() {
                if (finished) throw new NoSuchElementException();
                List<EntityDataDTO> records;
                var origin = plan.claims().origin();
                if ("CURRENT_RECORD".equals(plan.claims().dataScope())) {
                    records = List.of(data.findAccessibleById(plan.scheme().entityCode(), origin.getRecordId(), origin.getListKey()));
                    finished = true;
                } else {
                    var batch = lists.findExportBatchWithResolvedConfig(plan.scheme().entityCode(), plan.list() == null ? origin.getListKey() : plan.list().getListKey(), plan.list(),
                            resolver.filters(plan), "SELECTED".equals(plan.claims().dataScope()) ? origin.getSelectedIds() : null, cursor);
                    records = batch.records();
                    if (!records.isEmpty() && cursor != null && cursor.equals(batch.nextCursor()))
                        throw new IllegalStateException("导出查询游标未前进");
                    cursor = batch.nextCursor(); finished = records.isEmpty() || cursor == null;
                }
                if (count.addAndGet(records.size()) > MAX_EXPORT_ROWS) throw new IllegalArgumentException("单次导出超过100000行，请缩小筛选范围");
                formatter.prepareBatch(records);
                return records.stream().map(formatter::formatRow).toList();
            }
        };
    }

    /** 模板与本次导入使用同一份已校验方案，说明页不会参与数据读取。 */
    public void template(String token, OutputStream out) {
        var plan = resolver.resolve(token, "TEMPLATE");
        var headers = codec.heads(plan, true);
        // 在输出任何字节前解析全部说明和字典；配置失败应返回正常错误响应，不能下载到半个模板。
        var instructions = codec.importInstructions(plan);
        var style = new com.alibaba.excel.write.metadata.style.WriteCellStyle();
        var format = new com.alibaba.excel.metadata.data.DataFormatData();
        format.setIndex((short) 49);
        style.setDataFormatData(format);
        try (var writer = com.alibaba.excel.EasyExcel.write(out).autoCloseStream(false)
                .registerWriteHandler(new com.alibaba.excel.write.style.HorizontalCellStyleStrategy(null, style))
                .build()) {
            writer.write(List.of(), com.alibaba.excel.EasyExcel.writerSheet(0, plan.scheme().document().file().sheetName())
                    .head(headers).registerWriteHandler(sheetStyle(plan, true)).build());
            String instructionSheet = "导入说明".equals(plan.scheme().document().file().sheetName()) ? "字段说明" : "导入说明";
            writer.write(instructions, com.alibaba.excel.EasyExcel.writerSheet(1, instructionSheet)
                    .registerWriteHandler(new com.alibaba.excel.write.style.column.SimpleColumnWidthStyleStrategy(48))
                    .head(List.of(List.of("列标题"), List.of("填写要求"), List.of("输入格式"), List.of("校验说明"), List.of("可用字典值"))).build());
        }
    }

    /** 空模板尚无数据单元格，必须设置默认列格式才能保留用户随后输入的前导零和长编号。 */
    private com.alibaba.excel.write.handler.SheetWriteHandler sheetStyle(EntityExcelRuntimeResolver.Plan plan, boolean importing) {
        // IGNORE 的表单外列不进入模板，列宽也须按相同映射重排，不能套用被忽略列的宽度。
        var columns = importing ? ExcelImportFormColumnPolicy.resolve(plan).importColumns()
                : plan.scheme().document().columns().stream().filter(column -> column.exportEnabled()).toList();
        return new com.alibaba.excel.write.handler.SheetWriteHandler() {
            @Override public void afterSheetCreate(com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder workbook,
                    com.alibaba.excel.write.metadata.holder.WriteSheetHolder sheet) {
                var textStyle = workbook.getWorkbook().createCellStyle();
                textStyle.setDataFormat((short) 49);
                for (int i = 0; i < columns.size(); i++) {
                    int width = columns.get(i).width() == null ? 24 : columns.get(i).width();
                    sheet.getSheet().setColumnWidth(i, Math.min(255, Math.max(1, width)) * 256);
                    if (importing) sheet.getSheet().setDefaultColumnStyle(i, textStyle);
                }
            }
        };
    }

    /** 任务结果只包含可公开的统计和下载描述，存储地址及受理配置不返回浏览器。 */
    public Map<String, Object> get(String id) {
        Map<String, Object> job = authorizeJob(id);
        Map<String, Object> result = new LinkedHashMap<>(jobs.readMap((String) job.get("counters_json")));
        result.remove("savedExcelRows");
        result.put("id", id); result.put("jobId", id); result.put("status", job.get("status")); result.put("operation", job.get("operation"));
        result.put("message", job.get("error_message"));
        if (expired(job)) {
            result.put("files", List.of()); result.put("errors", List.of());
            result.put("filesExpired", true); result.put("errorsExpired", true);
            result.put("message", "任务文件和错误明细已超过24小时保留期");
            return result;
        }
        List<Map<String, Object>> descriptors = new ArrayList<>();
        addFile(result, descriptors, job.get("output_file_id"), "RESULT");
        addFile(result, descriptors, job.get("error_file_id"), "ERROR_REPORT");
        result.put("files", descriptors);
        result.put("errors", jobs.errors(id, 1, 50).get("records"));
        return result;
    }

    public Map<String, Object> errors(String id, int page, int size) {
        var job = authorizeJob(id);
        if (expired(job)) return Map.of("records", List.of(), "total", 0, "expired", true);
        return jobs.errors(id, page, size);
    }

    public StoredFile download(String id, String kind) throws IOException {
        Map<String, Object> job = authorizeJob(id);
        if (expired(job)) throw new ResponseStatusException(HttpStatus.GONE, "任务文件已超过24小时保留期，请重新执行");
        String column = switch (kind) { case "RESULT" -> "output_file_id"; case "ERROR_REPORT" -> "error_file_id";
            default -> throw new IllegalArgumentException("不支持的任务文件类型"); };
        if (job.get(column) == null) throw new IllegalArgumentException("任务文件不存在或已过期");
        return files.open(String.valueOf(job.get(column)));
    }

    private Map<String, Object> authorizeJob(String id) {
        Map<String, Object> job = jobs.requireOwned(id);
        String entity = String.valueOf(job.get("entity_code"));
        if ("EXPORT".equals(job.get("operation"))) {
            var plan = jobs.readMap((String) job.get("config_json"));
            Map<?, ?> claims = (Map<?, ?>) plan.get("claims");
            permissions.requireStandardPermission(entity, "FILTERED".equals(claims.get("dataScope")) ? EntityPermissionAction.EXPORT_ALL : EntityPermissionAction.EXPORT);
        } else {
            permissions.requireStandardPermission(entity, EntityPermissionAction.IMPORT);
            permissions.requireStandardPermission(entity, EntityPermissionAction.CREATE);
        }
        return job;
    }

    private void addFile(Map<String, Object> result, List<Map<String, Object>> descriptors, Object id, String kind) {
        if (id == null) return;
        var file = files.metadata(String.valueOf(id));
        String name = String.valueOf(file.get("original_name"));
        descriptors.add(Map.of("fileKind", kind, "fileName", name, "contentType", file.get("content_type")));
        result.put("RESULT".equals(kind) ? "fileName" : "errorFileName", name);
        result.put("contentType", EntityExcelFileStore.XLSX);
    }

    private void reject(String id, EntityExcelCodecService.Parsed parsed, List<Map<String, Object>> errors, String status, String message) {
        String report = null;
        try { report = errorReport(parsed, errors); }
        catch (Exception ex) { log.warn("Excel错误报告生成失败: jobId={}, failureType={}", id, ex.getClass().getSimpleName()); }
        String fileId = report;
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            boolean updated = Boolean.TRUE.equals(tx.execute(ignored -> {
                if (!jobs.lockIfRunning(id)) return false;
                jobs.saveErrors(id, errors);
                jobs.finish(id, status, counters(parsed, errors, 0), null, null, fileId, message);
                return true;
            }));
            // 超时清理可能已结束任务，后到的工作线程不能覆盖终态，刚生成的报告也不能成为孤立对象。
            if (!updated && fileId != null) deleteUnlinkedFile(fileId);
        } catch (RuntimeException failure) {
            if (fileId != null) deleteUnlinkedFile(fileId);
            throw failure;
        }
    }

    /** 清理异常不遮盖原任务结果，后端日志保留孤立对象身份便于重试清理。 */
    private void deleteUnlinkedFile(String fileId) {
        try { files.delete(fileId); }
        catch (RuntimeException failure) { log.warn("Excel孤立文件清理失败: fileId={}", fileId); }
    }

    private String errorReport(EntityExcelCodecService.Parsed parsed, List<Map<String, Object>> errors) throws IOException {
        List<Integer> columns = parsed == null ? List.of() : parsed.headers().keySet().stream().sorted().toList();
        List<List<String>> heads = new ArrayList<>();
        heads.add(List.of("Sheet")); heads.add(List.of("Excel行号"));
        columns.forEach(col -> heads.add(List.of(parsed.headers().get(col)))); heads.add(List.of("错误原因"));
        List<List<?>> rows = new ArrayList<>();
        if (parsed != null) {
            for (var row : parsed.rows()) {
                List<String> messages = errors.stream().filter(e -> Objects.equals(e.get("rowNumber"), row.rowNumber())
                        && Objects.equals(e.get("sheetNo"), row.sheetNo())).map(e -> String.valueOf(e.get("message"))).toList();
                if (messages.isEmpty()) continue;
                List<Object> values = new ArrayList<>(); values.add(row.sheetName()); values.add(row.rowNumber());
                columns.forEach(col -> values.add(reportText(row.rawValues().getOrDefault(col, ""))));
                values.add(reportText(String.join("；", messages))); rows.add(values);
            }
        }
        for (var error : errors) {
            if (parsed != null && ((Number) error.getOrDefault("rowNumber", 0)).intValue() > 0) continue;
            List<Object> values = new ArrayList<>(); values.add(error.getOrDefault("sheetName", "")); values.add(error.getOrDefault("rowNumber", 0));
            columns.forEach(col -> values.add("")); values.add(reportText(String.valueOf(error.get("message")))); rows.add(values);
        }
        Path report = Files.createTempFile("workflow-excel-errors-", ".xlsx");
        try {
            try (OutputStream out = Files.newOutputStream(report)) { ExcelUtils.writeRows(out, heads, rows, "错误明细"); }
            return files.put(report, "Excel导入导出错误报告.xlsx", EntityExcelFileStore.XLSX, UserContext.getUserId());
        } finally { Files.deleteIfExists(report); }
    }

    private Map<String, Object> counters(EntityExcelCodecService.Parsed parsed, List<Map<String, Object>> errors, long saved) {
        long dataCount = parsed == null ? 0 : parsed.dataRowCount();
        long invalid = errors.stream().filter(e -> ((Number) e.getOrDefault("rowNumber", 0)).intValue() > 0)
                .map(e -> e.get("sheetNo") + ":" + e.get("rowNumber")).distinct().count();
        // 截断明细仍沿用解析器完整计数；文件级失败不将未完成检查的行标成通过。
        if (parsed != null) invalid = Math.max(invalid, parsed.invalidRowCount());
        boolean fileError = errors.stream().anyMatch(e -> ((Number) e.getOrDefault("rowNumber", 0)).intValue() == 0);
        return Map.of("dataRowCount", dataCount, "skippedRowCount", parsed == null ? 0 : parsed.skippedRowCount(),
                "validRowCount", fileError ? 0 : Math.max(0, dataCount - invalid), "invalidRowCount", invalid, "savedRowCount", saved,
                "errorCount", errors.size(), "errorsTruncated", parsed != null && parsed.errorsTruncated()
                        || errors.stream().anyMatch(error -> "ERROR_LIMIT".equals(error.get("errorCode"))));
    }

    private String payloadHash(EntityExcelRuntimeResolver.Plan plan, String operation, String fileHash) {
        var c = plan.claims(); var source = c.origin();
        return tokens.hash(List.of(operation, fileHash, c.sourceHash(), c.schemeHash(), c.defaultHash(),
                c.entityHash(), c.writeFormHash(), c.dataScope(), source.getSelectedIds() == null ? List.of() : source.getSelectedIds(),
                source.getRecordId() == null ? "" : source.getRecordId(), resolver.filters(plan)));
    }
    private String exportName(EntityExcelRuntimeResolver.Plan plan) {
        String name = plan.scheme().document().exportConfig().fileNamePattern();
        if (name == null || name.isBlank()) name = plan.scheme().document().name() + "_{yyyyMMddHHmmss}";
        for (String pattern : List.of("yyyyMMddHHmmss", "yyyyMMdd", "yyyy-MM-dd"))
            name = name.replace("{" + pattern + "}", LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern)));
        name = EntityExcelRuntimeResolver.safeName(name);
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx") ? name : name + ".xlsx";
    }
    private static Map<String, Object> error(EntityExcelCodecService.Row row, String code, String message) {
        return Map.of("sheetNo", row == null ? 0 : row.sheetNo(), "sheetName", row == null ? "" : row.sheetName(),
                "rowNumber", row == null ? 0 : row.rowNumber(), "errorCode", code, "message", message);
    }

    /** 多列错误会合并到一个说明单元格，控制在Excel文本上限内，完整明细仍可通过任务分页查看。 */
    private static String reportText(String value) {
        if (value.length() <= 32767) return value;
        int end = Character.isHighSurrogate(value.charAt(32000 - 1)) ? 31999 : 32000;
        return value.substring(0, end) + "……（内容过长，已截断；完整错误请查看任务明细）";
    }

    /** 所有阶段共享一个明细预算，保留文件级截断提示，避免表单与批量校验叠加突破上限。 */
    private static void appendError(List<Map<String, Object>> errors, Map<String, Object> issue) {
        if (errors.size() < MAX_ERRORS) errors.add(issue);
        else if (errors.size() == MAX_ERRORS) errors.add(error(null, "ERROR_LIMIT", "错误明细超过10000条，后续明细已省略"));
    }

    private void appendCodecErrors(List<Map<String, Object>> errors, List<EntityExcelCodecService.Error> issues) {
        for (var issue : issues) appendError(errors, mapper.convertValue(issue, new TypeReference<>() {}));
    }
    private static String trace(String job, EntityExcelCodecService.Row row) { return "excel_" + job + "_" + row.sheetNo() + "_" + row.rowNumber(); }
    private static boolean expired(Map<String, Object> job) {
        Object created = job.get("created_at");
        LocalDateTime time = created instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime()
                : created instanceof LocalDateTime dateTime ? dateTime : null;
        return time != null && time.isBefore(LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(24));
    }
    private static String safeMessage(Throwable ex) {
        // EasyExcel 会包装表头、行数和 Sheet 校验异常，保留可操作的业务原因而不返回解析堆栈。
        if (ex instanceof com.alibaba.excel.exception.ExcelAnalysisException) {
            Throwable cause = ex.getCause();
            for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                if (cause instanceof IllegalArgumentException) return Objects.toString(cause.getMessage(), "Excel文件校验失败");
            }
            return "无法读取Excel文件，请检查文件格式、Sheet名称和表头";
        }
        if (ex instanceof IllegalArgumentException || ex instanceof com.workflow.core.error.ForbiddenException
                || ex instanceof com.workflow.core.error.BusinessConflictException
                || ex.getClass().getSimpleName().startsWith("FormSubmission")) return Objects.toString(ex.getMessage(), "数据校验失败");
        String message = Objects.toString(ex.getMessage(), "");
        return message.startsWith("字段必填:") || message.startsWith("字段值已存在:") ? message : "处理失败，请检查配置或联系管理员";
    }
    private static String digest(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) { byte[] bytes = new byte[8192]; int n;
                while ((n = in.read(bytes)) != -1) digest.update(bytes, 0, n); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    /** 即使上传声明的大小不可信，也按方案限制实际读取字节；超限发生在认领任务和保存业务数据之前。 */
    private static void copyBounded(InputStream in, OutputStream out, int maxFileSizeMb) throws IOException {
        long maximum = maxFileSizeMb * 1024L * 1024L;
        byte[] buffer = new byte[8192]; long total = 0; int count;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > maximum) throw new IllegalArgumentException(fileSizeMessage(maxFileSizeMb));
            out.write(buffer, 0, count);
        }
    }
    private static String fileSizeMessage(int maxFileSizeMb) {
        return "Excel 文件大小不能超过 " + maxFileSizeMb + " MB";
    }
    private static final class RowSaveException extends RuntimeException {
        final EntityExcelCodecService.Row row;
        RowSaveException(EntityExcelCodecService.Row row, RuntimeException cause) { super(cause); this.row = row; }
    }
    private static final class FileValidationException extends RuntimeException {
        FileValidationException(String message) { super(message); }
    }
    private static final class ProcessedValidationException extends RuntimeException {
        final List<EntityExcelCodecService.Error> errors;
        ProcessedValidationException(List<EntityExcelCodecService.Error> errors) { this.errors = List.copyOf(errors); }
    }
}
