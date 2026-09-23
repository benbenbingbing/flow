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

    /**
     * 初始化嵌入式原生流程运行时适配器，保存构造参数供后续方法使用。
     *
     * @param runtimeService 运行时服务依赖，保存到当前对象供后续业务方法调用
     * @param historyService 历史服务依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedNativeProcessRuntimeAdapter(
            RuntimeService runtimeService,
            HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    /**
     * 运行中实例优先读 runtime，已结束实例回退到 history。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 匹配的记录目标；未找到时为空
     */
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

    /**
     * 生成历史文本，供后续匹配或展示。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param variableName 变量名称，后续用于处理历史时匹配或展示
     * @return 处理后的历史文本，供调用方比较或展示
     */
    private String historic(String processInstanceId, String variableName) {
        HistoricVariableInstance value = historyService
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(variableName)
                .singleResult();
        return value == null ? null : text(value.getValue());
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        String result = value == null ? null : String.valueOf(value).trim();
        return StringUtils.hasText(result) ? result : null;
    }
}
