package com.workflow.runner;

import com.workflow.service.EntityDataDynamicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 回填能够明确识别的历史撤回流程实体状态。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class WithdrawnEntityDataBackfillRunner implements ApplicationRunner {

    private final HistoryService historyService;
    private final EntityDataDynamicService entityDataDynamicService;

    @Override
    public void run(ApplicationArguments args) {
        List<HistoricProcessInstance> instances = historyService
                .createHistoricProcessInstanceQuery()
                .finished()
                .list();
        int updated = 0;
        for (HistoricProcessInstance instance : instances) {
            if (instance.getDeleteReason() == null
                    || !instance.getDeleteReason().startsWith("发起人撤回")) {
                continue;
            }
            String entityCode = historicVariable(instance.getId(), "entityCode");
            String entityDataId = historicVariable(instance.getId(), "entityDataId");
            if (!StringUtils.hasText(entityCode) || !StringUtils.hasText(entityDataId)) {
                continue;
            }
            try {
                entityDataDynamicService.markWithdrawn(entityCode, entityDataId);
                updated++;
            } catch (Exception e) {
                log.warn(
                        "历史撤回状态回填失败: processInstanceId={}, entityCode={}, entityDataId={}, error={}",
                        instance.getId(),
                        entityCode,
                        entityDataId,
                        e.getMessage());
            }
        }
        if (updated > 0) {
            log.info("历史撤回实体状态回填完成: updated={}", updated);
        }
    }

    private String historicVariable(String processInstanceId, String variableName) {
        var variable = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(variableName)
                .singleResult();
        return variable == null || variable.getValue() == null
                ? null
                : String.valueOf(variable.getValue());
    }
}
