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

    /**
     * 应用启动入口：查询已结束的历史流程实例，识别发起人撤回的流程并将对应实体数据标记为撤回状态。
     *
     * @param args 启动参数（本 Runner 未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        List<HistoricProcessInstance> instances = historyService
                .createHistoricProcessInstanceQuery()
                .finished()
                .list();
        int updated = 0;
        for (HistoricProcessInstance instance : instances) {
            // 仅处理「发起人撤回」类删除原因的流程
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

    /**
     * 读取指定流程实例的历史变量值。
     *
     * @param processInstanceId 流程实例ID
     * @param variableName      变量名
     * @return 变量字符串值；变量不存在或为空时返回 {@code null}
     */
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
