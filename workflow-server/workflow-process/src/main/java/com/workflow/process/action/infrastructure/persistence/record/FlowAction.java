package com.workflow.process.action.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程动作配置
 * 用于流程、节点和顺序流上配置的接口动作
 */
@Data
@TableName("process_action")
public class FlowAction {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 流程定义配置ID
     */
    private String processConfigId;
    
    /**
     * 作用域：PROCESS、NODE、SEQUENCE_FLOW
     */
    private String scopeType;

    /**
     * BPMN 元素 ID；流程级动作为空
     */
    private String elementId;

    /**
     * 业务触发时机
     */
    private String triggerTiming;

    /**
     * 执行方式：IN_TRANSACTION、AFTER_COMMIT
     */
    private String executionMode;

    /**
     * 失败策略：ROLLBACK、CONTINUE、RETRY、IGNORE
     */
    private String failurePolicy;

    /**
     * 重试配置 JSON
     */
    private String retryConfig;

    /**
     * 动作定义目录 ID；interfaceName 继续作为发布快照保留。
     */
    private String actionDefinitionId;
    
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
    
    /**
     * 定义状态的可选值；调用方据此选择对应的处理分支。
     */
    public enum Status {
        DRAFT("草稿"),
        PUBLISHED("已发布"),
        DISABLED("已禁用");
        
        private final String label;
        
        /**
         * 初始化状态，保存构造参数供后续方法使用。
         *
         * @param label 标签依赖，保存到当前对象供后续业务方法调用
         */
        Status(String label) {
            this.label = label;
        }
        
        /**
         * 读取标签；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的标签文本，供调用方比较或展示
         */
        public String getLabel() {
            return label;
        }
    }
}
