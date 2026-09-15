package com.workflow.process.instance.application;

import com.workflow.contracts.process.port.ProcessRecordReadAccessPort;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/** 已办、知会与流程进度的表单按钮复用流程详情的只读授权。 */
@Component
@RequiredArgsConstructor
public class ProcessRecordReadAccessAdapter implements ProcessRecordReadAccessPort {

    private final ProcessInstanceAccessService instanceAccessService;
    private final HistoryService historyService;
    private final ProcessPublishedSnapshotService publishedSnapshotService;

    @Override
    @Transactional(readOnly = true)
    public void requireReadAccess(String entityCode, String recordId,
                                  String processInstanceId, String processVersionHistoryId) {
        if (!StringUtils.hasText(entityCode) || !StringUtils.hasText(recordId)
                || !StringUtils.hasText(processInstanceId)
                || !StringUtils.hasText(processVersionHistoryId)) {
            throw contextMismatch();
        }
        // 实例可见性包含历史办理人和知会收件人；不能改成仅查当前待办，
        // 也不能把浏览器携带的流程 ID 本身当成记录授权。
        instanceAccessService.requireReadAccess(processInstanceId);
        var instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (instance == null || !StringUtils.hasText(instance.getProcessDefinitionId())
                || !Objects.equals(entityCode, processVariable(processInstanceId, "entityCode"))
                || !Objects.equals(recordId, processVariable(processInstanceId, "entityDataId"))) {
            throw contextMismatch();
        }
        // 历史变量同时覆盖运行中和已结束实例；版本始终从实际部署解析，
        // 防止用同一表单在另一流程版本签发的令牌读取当前记录。
        var history = publishedSnapshotService.getVersionByProcessDefinitionId(
                instance.getProcessDefinitionId());
        if (history == null || !Objects.equals(processVersionHistoryId, history.getId())) {
            throw contextMismatch();
        }
    }

    /** 仅读取流程绑定的系统坐标；不使用用户提交的表单值补齐缺失的绑定。 */
    private Object processVariable(String processInstanceId, String name) {
        var variable = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId).variableName(name).singleResult();
        return variable == null ? null : variable.getValue();
    }

    private BusinessForbiddenException contextMismatch() {
        return new BusinessForbiddenException("PROCESS_FORM_READ_CONTEXT_MISMATCH",
                "查看表单与流程实例的业务记录或发布版本不一致");
    }
}
