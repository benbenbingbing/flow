package com.workflow.entity.runtime;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;

/**
 * 实体运行时与工作流引擎之间的端口接口。
 * 由工作流侧实现，供实体侧在数据提交/流转时驱动流程并回写任务信息。
 */
public interface EntityWorkflowRuntimePort {

    /**
     * 根据实体数据与定义启动对应的流程实例。
     *
     * @param dto        实体数据传输对象（携带业务数据与提交人信息）
     * @param definition 实体定义（用于解析绑定的流程）
     */
    void startProcess(EntityDataDTO dto, EntityDefinition definition);

    /**
     * 更新实体数据当前所处的流程任务信息。
     *
     * @param entityCode          实体编码
     * @param entityDataId        实体数据ID
     * @param currentTaskId       当前任务ID
     * @param currentTaskName     当前任务名称
     * @param currentTaskAssignee 当前任务办理人
     */
    void updateCurrentTask(String entityCode,
                           String entityDataId,
                           String currentTaskId,
                           String currentTaskName,
                           String currentTaskAssignee);
}
