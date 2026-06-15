package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
/**
 * 流程动作配置
 * 用于顺序流（SequenceFlow）上配置的接口动作
 */
@Data
@TableName("flow_action")
public class FlowAction {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 流程定义配置ID
     */
    private String processConfigId;
    
    /**
     * 顺序流ID（bpmn元素ID）
     */
    private String sequenceFlowId;
    
    /**
     * 动作名称
     */
    private String actionName;
    
    /**
     * 动作描述
     */
    private String description;
    
    /**
     * 接口地址（Spring Bean名称或完整类名）
     */
    private String interfaceName;
    
    /**
     * 方法名
     */
    private String methodName;
    
    /**
     * 参数JSON（用于传递给接口的参数）
     */
    private String paramsJson;
    
    /**
     * 执行顺序（越小越先执行）
     */
    private Integer sortOrder;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 状态：DRAFT-草稿，PUBLISHED-已发布，DISABLED-已禁用
     */
    private String status;
    
    /**
     * 所属版本ID（发布时关联到版本）
     */
    private String versionId;
    
    /**
     * 创建时间
     */
        @TableField("create_time")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
        @TableField("update_time")
    private LocalDateTime updatedAt;
    
    /**
     * 创建人
     */
    private String createdBy;
    
    /**
     * 是否删除 0-未删除 1-已删除
     */
    private Integer deleted;
    
    public enum Status {
        DRAFT("草稿"),
        PUBLISHED("已发布"),
        DISABLED("已禁用");
        
        private final String label;
        
        Status(String label) {
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }
}
