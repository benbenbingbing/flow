package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程草稿箱
 */
@Data
@TableName("process_draft")
public class ProcessDraft {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String draftCode;
    private String processDefinitionId;
    private String processName;
    private String entityCode;
    private String entityDataId;
    private String businessKey;
    private String formData;
    private String draftTitle;
    private String draftSummary;
    private String userId;
    private String userName;
    
    /**
     * 状态：ACTIVE有效/SUBMITTED已提交/DELETED已删除
     */
    private String status;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
