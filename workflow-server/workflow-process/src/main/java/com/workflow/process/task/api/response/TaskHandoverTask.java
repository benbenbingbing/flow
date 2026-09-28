package com.workflow.process.task.api.response;

import java.time.LocalDateTime;
import lombok.Data;

/** 人员交接只展示定位任务所需的摘要，不返回业务表单、附件或审批内容。 */
@Data
public class TaskHandoverTask {
    private String taskId;
    private String taskName;
    private String processInstanceId;
    private String processName;
    private String entityCode;
    private String entityDataId;
    private String businessName;
    private String businessCode;
    private LocalDateTime createTime;
    private String assigneeName;
    private String nodeType;
    /** ASSIGNED 为已分配，CANDIDATE 为共享候选，ADD_SIGN 为本地加签任务。 */
    private String assignmentType;
    /** 保留 todo / waiting / hold；交接不会激活后加签或越过加签等待。 */
    private String status;
}
