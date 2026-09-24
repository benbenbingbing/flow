package com.workflow.entity.data.application;

import com.workflow.contracts.process.port.TaskBusinessSummaryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/** 合并同一业务事务的多次写入，以最终落库值刷新任务摘要；通过 contracts 避免模块循环依赖。 */
@Component
@RequiredArgsConstructor
public class EntityTaskSummaryRefresh {
    private final ObjectProvider<TaskBusinessSummaryPort> ports;

    /** 每次主表/关系子记录写入后登记；提交前仍在原事务，失败会与业务写入一起回滚。 */
    public void changed(String entityCode, String recordId) {
        if (entityCode == null || recordId == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("任务摘要刷新必须在实体写入事务内登记");
        }
        Pending pending = TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(Pending.class::isInstance).map(Pending.class::cast)
                .filter(value -> value.owner == this).findFirst().orElse(null);
        if (pending == null) {
            pending = new Pending(this);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.records.add(new RecordKey(entityCode, recordId));
    }

    private record RecordKey(String entityCode, String recordId) { }

    private final class Pending implements TransactionSynchronization {
        private final EntityTaskSummaryRefresh owner;
        private final Set<RecordKey> records = new LinkedHashSet<>();
        private Pending(EntityTaskSummaryRefresh owner) { this.owner = owner; }

        @Override public void beforeCommit(boolean readOnly) {
            records.stream().sorted(Comparator.comparing(RecordKey::entityCode).thenComparing(RecordKey::recordId))
                    .forEach(key -> ports.orderedStream().forEach(port ->
                            port.refreshTaskBusinessSummary(key.entityCode(), key.recordId())));
        }
    }
}
