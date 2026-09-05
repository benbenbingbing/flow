package com.workflow.process.instance.infrastructure;

import com.workflow.contracts.embed.runtime.port.EmbedNativeProcessRuntimePort;
import java.util.Optional;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 从 Flowable 实例变量恢复 Embed 流程读取所属的业务记录。 */
@Component
public class EmbedNativeProcessRuntimeAdapter
        implements EmbedNativeProcessRuntimePort {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public EmbedNativeProcessRuntimeAdapter(
            RuntimeService runtimeService,
            HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    /** 运行中实例优先读 runtime，已结束实例回退到 history。 */
    @Override
    public Optional<RecordTarget> findRecordTarget(
            String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return Optional.empty();
        }
        String entityCode = null;
        String recordId = null;
        if (runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult() != null) {
            entityCode = text(runtimeService.getVariable(
                    processInstanceId, "entityCode"));
            recordId = text(runtimeService.getVariable(
                    processInstanceId, "entityDataId"));
        }
        if (!StringUtils.hasText(entityCode)) {
            entityCode = historic(processInstanceId, "entityCode");
        }
        if (!StringUtils.hasText(recordId)) {
            recordId = historic(processInstanceId, "entityDataId");
        }
        return StringUtils.hasText(entityCode) && StringUtils.hasText(recordId)
                ? Optional.of(new RecordTarget(entityCode, recordId))
                : Optional.empty();
    }

    private String historic(String processInstanceId, String variableName) {
        HistoricVariableInstance value = historyService
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(variableName)
                .singleResult();
        return value == null ? null : text(value.getValue());
    }

    private static String text(Object value) {
        String result = value == null ? null : String.valueOf(value).trim();
        return StringUtils.hasText(result) ? result : null;
    }
}
