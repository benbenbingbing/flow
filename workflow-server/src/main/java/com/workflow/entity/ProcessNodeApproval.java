package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点审批配置
 */
@Data
@TableName("process_node_approval")
public class ProcessNodeApproval {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 流程配置ID
     */
    private String processConfigId;
    
    /**
     * 节点ID（bpmn元素ID）
     */
    private String nodeId;
    
    /**
     * 节点名称
     */
    private String nodeName;
    
    /**
     * 是否启用审批意见：0-否 1-是
     */
    private Integer enabled;
    
    /**
     * 审批意见标签
     */
    private String commentLabel;
    
    /**
     * 审批选项JSON
     */
    private String optionsJson;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
