package com.workflow.entity.runtime;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.publish.EntityPublishedSnapshot;
import com.workflow.entity.publish.EntityPublishedSnapshotService;
import com.workflow.listener.MultiInstanceCollectionListener;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.ProcessTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态实体流程运行时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityWorkflowRuntimeService {

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityStatusMapper entityStatusMapper;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final DynamicTableService dynamicTableService;
    private final RuntimeService runtimeService;
    private final IdentityService identityService;
    private final org.flowable.engine.TaskService taskService;
    private final ProcessTaskService processTaskService;
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;
    private final EntityPublishedSnapshotService snapshotService;

    public void startProcess(EntityDataDTO dto, EntityDefinition definition) {
        EntityPublishedSnapshot snapshot = snapshotService.getLatestByEntityCode(dto.getEntityCode());
        String processConfigId = snapshot.getProcessDefinitionId();
        if (!StringUtils.hasText(processConfigId)) {
            throw new RuntimeException("实体发布快照未绑定流程定义: " + dto.getEntityCode());
        }

        ProcessDefinitionConfig processConfig = processDefinitionConfigMapper.selectById(processConfigId);
        if (processConfig == null) {
            throw new RuntimeException("流程定义不存在: " + processConfigId);
        }
        if (processConfig.getStatus() == ProcessDefinitionConfig.ProcessStatus.DISABLED) {
            throw new RuntimeException("流程已禁用，无法发起: " + processConfig.getProcessName());
        }

        String processKey = processConfig.getProcessKey();
        Map<String, Object> variables = buildVariables(dto);
        multiInstanceCollectionListener.prepareVariables(processConfigId, variables);

        if (StringUtils.hasText(dto.getSubmitterId())) {
            identityService.setAuthenticatedUserId(dto.getSubmitterId());
        }

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey, dto.getId(), variables);
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();

        updateProcessFields(dto, processInstance, currentTask);
        processTaskService.syncTasksFromFlowable(processInstance.getId());

        log.info("实体数据 {} 发起流程 {}，流程实例ID: {}", dto.getId(), processKey, processInstance.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentTask(String entityCode, String entityDataId, String currentTaskId,
                                  String currentTaskName, String currentTaskAssignee) {
        String tableName = dynamicTableService.getTableName(entityCode);
        dynamicMapper.updateCurrentTask(tableName, entityDataId, currentTaskId, currentTaskName, currentTaskAssignee);
        log.debug("更新实体当前任务: entityCode={}, entityDataId={}, taskId={}, taskName={}, assignee={}",
                entityCode, entityDataId, currentTaskId, currentTaskName, currentTaskAssignee);
    }

    private Map<String, Object> buildVariables(EntityDataDTO dto) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("entityCode", dto.getEntityCode());
        variables.put("entityDataId", dto.getId());
        variables.put("dataNo", dto.getDataNo());
        variables.put("submitterId", dto.getSubmitterId());
        variables.put("submitterName", dto.getSubmitterName());
        variables.put("skipNodeEnabled", true);

        if (StringUtils.hasText(dto.getSubmitterId())) {
            variables.put("initiator", dto.getSubmitterId());
        }
        if (dto.getData() != null) {
            variables.putAll(dto.getData());
        }
        if (dto.getProcessVariables() != null) {
            variables.putAll(dto.getProcessVariables());
        }
        return variables;
    }

    private void updateProcessFields(EntityDataDTO dto, ProcessInstance processInstance, Task currentTask) {
        String processingStatus = getProcessingStatus(dto.getEntityCode());
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", dto.getId());
        updateData.put("process_instance_id", processInstance.getId());
        updateData.put("process_start_time", LocalDateTime.now());
        updateData.put("status", processingStatus);
        updateData.put("updated_at", LocalDateTime.now());

        if (currentTask != null) {
            updateData.put("current_task_id", currentTask.getId());
            updateData.put("current_task_name", currentTask.getName());
            updateData.put("current_task_assignee", currentTask.getAssignee());
        }

        String tableName = dynamicTableService.getTableName(dto.getEntityCode());
        dynamicMapper.update(tableName, updateData);

        dto.setProcessInstanceId(processInstance.getId());
        dto.setStatus(processingStatus);
        if (currentTask != null) {
            dto.setCurrentTaskId(currentTask.getId());
            dto.setCurrentTaskName(currentTask.getName());
            dto.setCurrentTaskAssignee(currentTask.getAssignee());
        }
    }

    private String getProcessingStatus(String entityCode) {
        try {
            List<EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, "PROCESSING");
            if (statuses != null && !statuses.isEmpty()) {
                return statuses.get(0).getStatusCode();
            }
        } catch (Exception e) {
            log.warn("获取实体[{}]流程中状态失败: {}", entityCode, e.getMessage());
        }
        return "PENDING";
    }
}
