package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常用审批意见
 */
@Data
@TableName("process_common_opinion")
public class ProcessCommonOpinion {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String userId;
    private String opinionContent;
    
    /**
     * 意见类型：APPROVE同意/REJECT驳回/TRANSFER转办
     */
    private String opinionType;
    
    private Integer sortOrder;
    private Integer useCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
