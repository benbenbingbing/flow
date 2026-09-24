package com.workflow.process.task.application;

import com.workflow.process.task.api.response.TaskVO;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.task.api.TaskInfo;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

/** 任务列表基础投影；业务摘要来源由读模型/实时回退策略分别补充。 */
final class TaskListViewMapper {
    private TaskListViewMapper() {}

    /** 保持任务时间使用本地时区、SLA 截止时间使用 UTC 的既有协议。 */
    static TaskVO fromMirror(ProcessTask task) {

        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setTaskName(task.getNodeName());
        vo.setNodeType(task.getNodeType());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setProcessName(task.getProcessName());
        vo.setAssignee(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setAssigneeType(task.getAssigneeType());
        vo.setClaimRequired("group".equalsIgnoreCase(task.getAssigneeType()));
        vo.setCanClaim("todo".equals(task.getStatus()) && "group".equalsIgnoreCase(task.getAssigneeType())
                && !"ADD_SIGN".equals(task.getNodeType()));
        vo.setStartUserName(task.getStartUserName());
        vo.setBusinessKey(task.getBusinessKey());
        vo.setCreateTime(date(task.getStartTime(), ZoneId.systemDefault()));
        vo.setEndTime(date(task.getEndTime(), ZoneId.systemDefault()));
        vo.setDuration(task.getDuration());
        vo.setPriority(task.getPriority());
        vo.setResult(task.getAction());
        vo.setComment(task.getComment());
        vo.setSlaStatus(task.getSlaStatus());
        vo.setResponseDueTime(date(task.getResponseDueTime(), ZoneOffset.UTC));
        vo.setDueTime(date(task.getDueTime(), ZoneOffset.UTC));
        vo.setEntityCode(task.getEntityCode());
        vo.setEntityDataId(task.getEntityDataId());
        vo.setFormKey(task.getFormKey());
        return vo;
    }

    /** 引擎任务的基础属性；认领能力与业务摘要由调用方补充。 */
    static TaskVO fromEngine(TaskInfo task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setCreateTime(task.getCreateTime());
        vo.setPriority(task.getPriority());
        vo.setAssignee(task.getAssignee());
        return vo;
    }

    private static Date date(LocalDateTime value, ZoneId zone) {
        return value == null ? null : Date.from(value.atZone(zone).toInstant());
    }
}
