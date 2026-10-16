package com.workflow.process.command.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.contracts.process.model.ProcessCommandReceipt;
import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.process.command.infrastructure.persistence.mapper.ProcessCommandRecordMapper;
import com.workflow.process.command.infrastructure.persistence.record.ProcessCommandRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

/**
 * 所有公共流程命令共用的事务与持久化幂等边界。
 * 回执与真实业务操作参与同一事务，异常直接向外传播；不在此处提交外部通知。
 */
@Service
@RequiredArgsConstructor
public class ProcessCommandExecutor {
    private final ProcessCommandRecordMapper records;
    private final JdbcWriteAttempt writeAttempt;
    private final ObjectMapper json;
    private final ProcessInvocationAccess access;
    private final ThreadLocal<Set<String>> executing = new ThreadLocal<>();

    /**
     * 首次执行持久化结果，重试返回原结果。相同请求键不同命令/载荷抛 IDEMPOTENCY_CONFLICT。
     * @param meta 由业务意图产生且重试复用的标识
     * @param commandType 稳定命令编码，参与摘要以禁止跨命令复用请求键
     * @param command 完整业务命令，不能含临时随机数或无关请求时间
     * @param resultType 已声明的结果类型，不从数据库反射加载类
     * @param work 真正业务操作；必须使用同一事务数据源，不自行吞错或提交
     * @return 回执及独立结果；外层事务存在时仍可能随后回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> Execution<T> execute(CommandMeta meta, String commandType, Object command,
                                    Class<T> resultType, Supplier<Outcome<T>> work) {
        String scope = access.invocationKey();
        String id = digest(scope + "\n" + meta.requestId());
        // 同事务中的动作回调可以再次进入公共端口；应在读取自己尚未完成的回执前报告重入。
        if (executing.get() != null && executing.get().contains(id)) throw new ProcessOperationException(
                "COMMAND_REENTRANT", "同一命令不能递归执行", id);
        String hash = digest(commandType + "\n" + encode(command));
        ProcessCommandRecord existing = records.selectById(id);
        if (existing != null) return replay(existing, hash, resultType);

        ProcessCommandRecord record = new ProcessCommandRecord();
        record.setId(id);
        record.setRequestId(meta.requestId());
        record.setInvocationScope(scope);
        record.setCommandType(commandType);
        record.setCommandHash(hash);
        record.setStatus("PENDING");
        record.setChanged(false);
        record.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        record.setUpdateTime(record.getCreateTime());
        try {
            // 唯一冲突可能令 PostgreSQL 事务失败，使用现有保存点工具恢复后再读取赢家回执。
            writeAttempt.execute(() -> records.insert(record));
        } catch (DuplicateKeyException duplicate) {
            ProcessCommandRecord winner = records.findForReplay(id);
            if (winner == null) throw duplicate;
            return replay(winner, hash, resultType);
        }

        Set<String> current = executing.get();
        if (current == null) { current = new HashSet<>(); executing.set(current); }
        if (!current.add(id)) throw new ProcessOperationException("COMMAND_REENTRANT", "同一命令不能递归执行", id);
        try {
            Outcome<T> outcome = work.get();
            record.setProcessInstanceId(outcome.processInstanceId());
            record.setTaskId(outcome.taskId());
            record.setChanged(outcome.changed());
            record.setResultDocument(encode(outcome.value()));
            record.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
            if (records.complete(record) != 1) throw new IllegalStateException("流程命令回执完成失败");
            return new Execution<>(receipt(record, false), outcome.value());
        } finally {
            current.remove(id);
            if (current.isEmpty()) executing.remove();
        }
    }

    /** 只有已提交的成功结果能够重放；不同载荷和进行中的请求不能被当作幂等成功。 */
    private <T> Execution<T> replay(ProcessCommandRecord record, String hash, Class<T> type) {
        if (!hash.equals(record.getCommandHash())) throw new ProcessOperationException(
                "IDEMPOTENCY_CONFLICT", "同一 requestId 不能用于不同流程操作", record.getId());
        if (!"SUCCESS".equals(record.getStatus())) throw new ProcessOperationException(
                "COMMAND_IN_PROGRESS", "流程操作正在执行，不能递归或重复推进", record.getId());
        try { return new Execution<>(receipt(record, true), json.readValue(record.getResultDocument(), type)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("流程命令回执无法读取", error); }
    }

    private ProcessCommandReceipt receipt(ProcessCommandRecord record, boolean replayed) {
        return new ProcessCommandReceipt(record.getRequestId(), record.getId(), record.getProcessInstanceId(),
                record.getTaskId(), Boolean.TRUE.equals(record.getChanged()), replayed);
    }

    private String encode(Object value) {
        try { return json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalArgumentException("流程命令或结果无法序列化", error); }
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** 内部执行结果携带实际解析的目标，避免在幂等重放前要求已完成任务仍然存在。 */
    public record Outcome<T>(String processInstanceId, String taskId, boolean changed, T value) {}
    /** 回执的重放标识与业务结果分开，不在重试时伪造新的业务结果。 */
    public record Execution<T>(ProcessCommandReceipt receipt, T value) {}
}
