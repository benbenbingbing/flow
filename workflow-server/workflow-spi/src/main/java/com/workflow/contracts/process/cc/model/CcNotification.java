package com.workflow.contracts.process.cc.model;

import lombok.Data;

/** 知会投递的独立数据快照；修改本对象不会回写收件箱记录，阅读状态由流程模块维护。 */
@Data
public class CcNotification {
    private String id;
    private String processInstanceId;
    private String processDefinitionId;
    private String processKey;
    private String processName;
    private String dataName;
    private String businessKey;
    private String nodeId;
    private String nodeName;
    private String ccUserId;
    private String ccUserName;
    private String ccType;
    private String ccTiming;
    private String operatorId;
    private String operatorName;
    private String comment;
    private String sourceTaskId;
    private String sourceType;
}
