package com.workflow.process.cc.application;

import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 在知会写入时固化显示名称。仅供创建入口使用，列表、统计和已读操作不调用本服务，
 * 因而流程/业务记录以后改名或删除都不会改变已发知会的显示内容。
 */
@Service
@RequiredArgsConstructor
public class ProcessCcSnapshotService {
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessVersionHistoryMapper versionMapper;
    private final ProcessDefinitionConfigMapper configMapper;
    private final EntityDataDynamicService entityDataService;

    /**
     * 补齐即将插入的知会名称，不覆盖调用方已提供的快照。
     * 流程名称优先使用当前实例所属发布版本；业务名称取创建知会时实体记录的标准 name 字段。
     * 未绑定实体数据的独立流程允许 dataName 为空；历史知会不会经过此入口补写。
     *
     * @param record 尚未持久化的知会记录，需包含流程定义/实例 ID
     */
    public void captureNames(ProcessCcRecord record) {
        if (!StringUtils.hasText(record.getProcessName())) {
            record.setProcessName(resolveProcessName(record));
        }
        if (!StringUtils.hasText(record.getDataName()) && StringUtils.hasText(record.getProcessInstanceId())) {
            Map<String, Object> variables = variables(record.getProcessInstanceId());
            String entityCode = text(variables.get("entityCode"));
            String entityDataId = text(variables.get("entityDataId"));
            if (StringUtils.hasText(entityCode) && StringUtils.hasText(entityDataId)) {
                var data = entityDataService.findById(entityCode, entityDataId);
                if (data != null) {
                    record.setDataName(data.getName());
                }
            }
        }
    }

    /**
     * BPMN 可以没有 name；按部署定位发布快照，避免旧实例误取新发布版本的名称。
     *
     * @param record 记录，供本方法解析流程名称时使用
     * @return 解析后的流程名称文本，供调用方比较或展示
     */
    private String resolveProcessName(ProcessCcRecord record) {
        ProcessDefinition definition = StringUtils.hasText(record.getProcessDefinitionId())
                ? repositoryService.createProcessDefinitionQuery()
                        .processDefinitionId(record.getProcessDefinitionId()).singleResult()
                : null;
        String processKey = definition == null ? record.getProcessKey() : definition.getKey();
        if (definition != null && StringUtils.hasText(definition.getDeploymentId())) {
            String publishedName = versionMapper.findByDeploymentId(definition.getDeploymentId())
                    .map(ProcessVersionHistory::getProcessName).orElse(null);
            if (StringUtils.hasText(publishedName)) {
                return publishedName;
            }
        }
        if (StringUtils.hasText(processKey)) {
            String configuredName = configMapper.findByProcessKey(processKey)
                    .map(ProcessDefinitionConfig::getProcessName).orElse(null);
            if (StringUtils.hasText(configuredName)) {
                return configuredName;
            }
        }
        return definition != null && StringUtils.hasText(definition.getName())
                ? definition.getName() : processKey;
    }

    /**
     * 最后一个任务完成后运行实例已删除，仍需从历史变量取得原实体定位信息。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 流程变量键值结果，供调用方继续处理
     */
    private Map<String, Object> variables(String processInstanceId) {
        try {
            return runtimeService.getVariables(processInstanceId);
        } catch (FlowableObjectNotFoundException exception) {
            Map<String, Object> variables = new HashMap<>();
            historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstanceId)
                    .list().forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
            return variables;
        }
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
