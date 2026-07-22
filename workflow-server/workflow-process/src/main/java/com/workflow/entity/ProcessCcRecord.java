package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程抄送记录
 * 存储流程抄送/知会信息
 */
@Data
@TableName("process_cc_record")
public class ProcessCcRecord {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 流程定义ID
     */
    private String processDefinitionId;
    
    /**
     * 流程Key
     */
    private String processKey;
    
    /**
     * 流程名称
     */
    private String processName;
    
    /**
     * 业务Key
     */
    private String businessKey;
    
    /**
     * 节点ID
     */
    private String nodeId;
    
    /**
     * 节点名称
     */
    private String nodeName;
    
    /**
     * 抄送人ID
     */
    private String ccUserId;
    
    /**
     * 抄送人名称
     */
    private String ccUserName;
    
    /**
     * 抄送类型：AUTO-自动抄送，MANUAL-手动抄送
     */
    private String ccType;
    
    /**
     * 抄送时机：START-流程发起，APPROVE-审批通过，REJECT-驳回，COMPLETE-流程结束
     */
    private String ccTiming;

    private String operatorId;

    private String operatorName;

    private String comment;

    private String sourceTaskId;

    private String sourceType;

    private String recipientRuleSnapshot;

    private String uniqueKey;
    
    /**
     * 阅读状态：UNREAD-未读，READ-已读
     */
    private String readStatus;
    
    /**
     * 阅读时间
     */
    private LocalDateTime readTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 删除标记
     */
    private Integer deleted;
}
