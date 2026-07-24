package com.workflow.entity.runtime;

import com.workflow.dto.EntityDataDTO;
import com.workflow.common.BusinessConflictException;
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
import com.workflow.service.WorkflowAutoSkipService;
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
 * 动态实体流程运行时服务。
 * <p>
 * 实现 {@link EntityWorkflowRuntimeService} 端口，负责基于动态实体数据发起流程、
 * 同步当前任务状态回写到动态实体表，并处理跳过节点自动完成等运行时逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityWorkflowRuntimeService implements EntityWorkflowRuntimePort {

    /** 动态实体数据Mapper，操作各实体对应的动态表 */
    private final EntityDataDynamicMapper dynamicMapper;
    /** 实体状态Mapper，查询实体状态码 */
    private final EntityStatusMapper entityStatusMapper;
    /** 流程定义配置Mapper */
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    /** 动态表服务，获取实体对应动态表名 */
    private final DynamicTableService dynamicTableService;
    /** Flowable运行时服务，发起流程实例 */
    private final RuntimeService runtimeService;
    /** Flowable身份服务，设置流程发起人 */
    private final IdentityService identityService;
    /** Flowable任务服务，查询当前任务 */
    private final org.flowable.engine.TaskService taskService;
    /** 流程任务服务，同步任务数据 */
    private final ProcessTaskService processTaskService;
    /** 跳过节点自动完成服务 */
    private final WorkflowAutoSkipService workflowAutoSkipService;
    /** 多实例会签收集监听器，准备会签变量 */
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;
    /** 实体发布快照服务，查询实体绑定的流程定义 */
    private final EntityPublishedSnapshotService snapshotService;
    /** 实体记录团队服务，记录发起流程事件 */
    private final com.workflow.service.EntityRecordTeamService entityRecordTeamService;

    /**
     * 基于实体数据发起流程实例。
     * <p>
     * 流程：获取实体发布快照绑定的流程定义 -> 校验流程已发布 -> 构建流程变量 ->
     * 设置发起人 -> 发起流程实例 -> 自动跳过配置的节点 -> 回写实体表当前任务。
     *
     * @param dto        实体数据DTO，包含实体编码、数据ID、发起人等
     * @param definition 实体定义，用于获取元数据
     * @throws BusinessConflictException 当实体未绑定流程定义、流程不存在或流程未发布时抛出
     */
    @Override
    public void startProcess(EntityDataDTO dto, EntityDefinition definition) {
        EntityPublishedSnapshot snapshot = snapshotService.getLatestByEntityCode(dto.getEntityCode());
        String processConfigId = snapshot.getProcessDefinitionId();
        if (!StringUtils.hasText(processConfigId)) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "实体发布快照未绑定流程定义: " + dto.getEntityCode());
        }

        ProcessDefinitionConfig processConfig = processDefinitionConfigMapper.selectById(processConfigId);
        if (processConfig == null) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "流程定义不存在: " + processConfigId);
        }
        if (processConfig.getStatus() != ProcessDefinitionConfig.ProcessStatus.PUBLISHED) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    processConfig.getStatus() == ProcessDefinitionConfig.ProcessStatus.DISABLED
                            ? "流程已禁用，无法发起: " + processConfig.getProcessName()
                            : "流程尚未发布，无法发起: " + processConfig.getProcessName());
        }

        String processKey = processConfig.getProcessKey();
        Map<String, Object> variables = buildVariables(dto);
        multiInstanceCollectionListener.prepareVariables(processConfigId, variables);

        if (StringUtils.hasText(dto.getSubmitterId())) {
            identityService.setAuthenticatedUserId(dto.getSubmitterId());
        }

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey, dto.getId(), variables);

        // 兜底：自动完成配置为跳过的节点（防止 flowable:skipExpression 未生效）。
        // 事件监听器负责中途到达的跳过节点，启动时由这里保证第一个跳过节点被处理。
        workflowAutoSkipService.autoSkipNodes(processInstance.getId(), processConfigId);

        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();

        updateProcessFields(dto, processInstance, currentTask);
        processTaskService.syncTasksFromFlowable(processInstance.getId());
        entityRecordTeamService.record(
                dto.getEntityCode(),
                dto.getId(),
                "START_PROCESS",
                "发起流程",
                processInstance.getId(),
                currentTask == null ? null : currentTask.getId());

        log.info("实体数据 {} 发起流程 {}，流程实例ID: {}", dto.getId(), processKey, processInstance.getId());
    }

    /**
     * 更新实体当前任务信息（流程节点流转时回写）。
     *
     * @param entityCode           实体编码，用于定位动态表
     * @param entityDataId         实体数据ID
     * @param currentTaskId         当前任务ID
     * @param currentTaskName       当前任务名称
     * @param currentTaskAssignee   当前任务处理人
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateCurrentTask(String entityCode, String entityDataId, String currentTaskId,
                                  String currentTaskName, String currentTaskAssignee) {
        String tableName = dynamicTableService.getTableName(entityCode);
        dynamicMapper.updateCurrentTask(tableName, entityDataId, currentTaskId, currentTaskName, currentTaskAssignee);
        log.debug("更新实体当前任务: entityCode={}, entityDataId={}, taskId={}, taskName={}, assignee={}",
                entityCode, entityDataId, currentTaskId, currentTaskName, currentTaskAssignee);
    }

    /**
     * 构建流程启动变量。
     * <p>
     * 注入实体编码、数据ID、数据编号、发起人、跳过节点开关，并合并实体业务数据与自定义流程变量。
     *
     * @param dto 实体数据DTO
     * @return 流程变量集合
     */
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

    /**
     * 将流程实例信息回写到动态实体表。
     * <p>
     * 更新实体表的流程实例ID、发起时间、状态、当前任务ID/名称/处理人，并同步更新DTO。
     *
     * @param dto             实体数据DTO
     * @param processInstance 流程实例
     * @param currentTask     当前活动任务，为空表示无活动任务
     */
    private void updateProcessFields(EntityDataDTO dto, ProcessInstance processInstance, Task currentTask) {
        String processingStatus = getProcessingStatus(dto.getEntityCode());
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", dto.getId());
        updateData.put("process_instance_id", processInstance.getId());
        updateData.put("process_start_time", LocalDateTime.now());
        updateData.put("status", processingStatus);
        updateData.put("update_time", LocalDateTime.now());

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

    /**
     * 获取实体"流程中"对应的状态码。
     * <p>
     * 查询实体状态表中分类为 PROCESSING 的状态，取第一个状态码；查询失败回退为 PENDING。
     *
     * @param entityCode 实体编码
     * @return 流程中状态码，默认 PENDING
     */
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
