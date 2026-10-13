package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Excel 任务及有界行报告。认领在独立短事务完成；业务成功状态由导入事务更新，
 * 失败状态在业务回滚之后更新，避免记录和任务状态分离提交。
 */
@Repository
@RequiredArgsConstructor
public class EntityExcelJobRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlatformTransactionManager transactions;

    /** 唯一键决定谁执行；并发重试只读取既有任务，不抢占正在运行的导入。 */
    public Claim claim(EntityExcelRuntimeResolver.Plan plan, String operation, String originKey,
            String requestId, String payloadHash) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:@-]{1,128}"))
            throw new IllegalArgumentException("requestId必须为1到128位字母数字或._:@-");
        String user = UserContext.getUserId();
        if (user == null) throw new ForbiddenException("请先登录");
        String id = UUID.randomUUID().toString().replace("-", "");
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        boolean acquired;
        try {
            transaction.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO entity_excel_job(id,entity_id,entity_code,operation,status,user_id,origin_key,
                      request_id,payload_hash,config_json,created_at,updated_at)
                    VALUES (?,?,?,?,'RUNNING',?,?,?,?,?,?,?)
                    """, id, plan.scheme().entityId(), plan.scheme().entityCode(), operation, user,
                    originKey, requestId, payloadHash, auditConfiguration(plan), now(), now()));
            acquired = true;
        } catch (DuplicateKeyException collision) { acquired = false; }
        var matches = jdbc.queryForList("SELECT * FROM entity_excel_job WHERE user_id=? AND origin_key=? AND request_id=?",
                user, originKey, requestId);
        if (matches.size() != 1) throw new IllegalStateException("Excel任务认领失败");
        Map<String, Object> job = matches.get(0);
        if (!Objects.equals(payloadHash, job.get("payload_hash")) || !Objects.equals(operation, job.get("operation")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一个requestId不能用于不同文件、操作或配置");
        return new Claim(String.valueOf(job.get("id")), acquired);
    }

    /** 下载、查询必须先执行此所有权校验，不能通过猜测任务ID读取他人数据。 */
    public Map<String, Object> requireOwned(String id) {
        var jobs = jdbc.queryForList("SELECT * FROM entity_excel_job WHERE id=?", id);
        if (jobs.size() != 1 || UserContext.getUserId() == null
                || !Objects.equals(jobs.get(0).get("user_id"), UserContext.getUserId()))
            throw new ForbiddenException("Excel任务不存在或无权访问");
        return jobs.get(0);
    }

    public void inputFile(String id, String fileId) {
        requireOne(jdbc.update("UPDATE entity_excel_job SET input_file_id=?,updated_at=? WHERE id=? AND status='RUNNING'",
                fileId, now(), id));
    }

    /** 每个成功批次在同一业务事务更新进度；进程中断后仍可准确报告已经提交的记录数。 */
    public void progress(String id, Map<String, Object> counters) {
        requireOne(jdbc.update("UPDATE entity_excel_job SET counters_json=?,updated_at=? WHERE id=? AND status='RUNNING'",
                json(counters), now(), id));
    }

    /** 与超时清理共用行锁；持锁直到整个业务事务提交，禁止已中断任务重新进入保存。 */
    public void lockRunning(String id) {
        if (!lockIfRunning(id)) throw new IllegalStateException("Excel任务已结束，不能继续写入");
    }

    /** 终态清理或失败收尾使用，返回false时保留既有终态，不能重复写入行报告。 */
    public boolean lockIfRunning(String id) {
        var status = jdbc.queryForList("SELECT status FROM entity_excel_job WHERE id=? FOR UPDATE", String.class, id);
        return status.size() == 1 && "RUNNING".equals(status.get(0));
    }

    /** 在调用方事务中结束任务；导入成功时本更新与全部业务记录原子提交。 */
    public void finish(String id, String status, Map<String, Object> counters, String fileId,
            String fileName, String errorFileId, String errorMessage) {
        requireOne(jdbc.update("""
                UPDATE entity_excel_job SET status=?,counters_json=?,output_file_id=?,file_name=?,
                  content_type=?,error_file_id=?,error_message=?,updated_at=? WHERE id=? AND status='RUNNING'
                """, status, json(counters), fileId, fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", errorFileId, errorMessage, now(), id));
    }

    /** 每行最多保存调用方已经裁剪的错误；不把整文件内容塞进任务摘要。 */
    public void saveErrors(String jobId, List<? extends Object> errors) {
        Map<String, List<Object>> grouped = new LinkedHashMap<>();
        for (Object error : errors) {
            Map<String, Object> value = mapper.convertValue(error, new TypeReference<>() {});
            int sheet = number(value.get("sheetNo"), 0);
            int row = number(value.get("rowNumber"), 0);
            grouped.computeIfAbsent(sheet + ":" + row, key -> new ArrayList<>()).add(error);
        }
        grouped.forEach((key, rowErrors) -> {
            String[] coordinates = key.split(":");
            jdbc.update("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number,errors_json) VALUES (?,?,?,?,?)",
                    UUID.randomUUID().toString().replace("-", ""), jobId,
                    Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), json(rowErrors));
        });
    }

    /** 有界任务最多1000行、10000条错误，分页在有限结果中进行，避免方言相关LIMIT。 */
    public Map<String, Object> errors(String id, int page, int size) {
        requireOwned(id);
        page = Math.max(1, page); size = Math.min(100, Math.max(1, size));
        List<Object> errors = new ArrayList<>();
        jdbc.query("SELECT errors_json FROM entity_excel_job_row WHERE job_id=? ORDER BY sheet_no,excel_row_number",
                rs -> { errors.addAll(readList(rs.getString(1))); }, id);
        long from = (long) (page - 1) * size;
        return Map.of("records", from >= errors.size() ? List.of() : errors.subList((int) from, (int) Math.min(from + size, errors.size())),
                "total", errors.size(), "pageNum", page, "pageSize", size);
    }

    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Excel任务文档损坏", ex); }
    }
    private List<Object> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Excel任务错误文档损坏", ex); }
    }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("Excel执行配置无法序列化", ex); }
    }

    /** 长期审计保留配置及其摘要，不持久化当前表单内容、筛选值、选择记录和短期执行令牌。 */
    private String auditConfiguration(EntityExcelRuntimeResolver.Plan plan) {
        var document = mapper.valueToTree(plan);
        var origin = document.path("claims").path("origin");
        if (origin instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.remove(List.of("input", "context", "selection", "selectedIds", "recordId", "serverIdempotencyKey",
                    "runtimeContext", "compositionContext"));
        }
        var selection = document.path("claims").path("selectionSource");
        if (selection instanceof com.fasterxml.jackson.databind.node.ObjectNode object) object.remove("runtimeContext");
        return json(document);
    }
    private static int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private static void requireOne(int changed) { if (changed != 1) throw new IllegalStateException("Excel任务状态已变化"); }
    public record Claim(String jobId, boolean acquired) { }
}
