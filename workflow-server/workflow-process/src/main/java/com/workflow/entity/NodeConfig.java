package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程节点配置实体类
 * 
 * @description 存储流程图中各个节点的配置信息
 *              包括节点ID、名称、类型、审批人、表单等
 *              对应数据库表：process_node_config
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
@TableName("process_node_config")
public class NodeConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 节点ID（在BPMN XML中的唯一标识）
     * 例如：UserTask_1, StartEvent_1
     */
    @TableField("node_id")
    private String nodeId;

    /**
     * 节点显示名称
     * 例如：部门经理审批、申请人填写
     */
    @TableField("node_name")
    private String nodeName;

    /**
     * 节点类型
     * START: 开始节点
     * END: 结束节点
     * USER_TASK: 用户任务节点
     * SERVICE_TASK: 服务任务节点
     * EXCLUSIVE_GATEWAY: 排他网关
     * PARALLEL_GATEWAY: 并行网关
     */
    @TableField("node_type")
    private NodeType nodeType;

    /**
     * 所属流程定义ID
     */
    @TableField("process_config_id")
    private String processConfigId;

    /**
     * 扩展配置JSON
     * 存储额外的节点配置信息
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 是否跳过此节点（仅第一个用户任务节点可设置）
     * true: 流程到达此节点后自动跳转到下一节点
     * false: 正常处理
     */
    @TableField("skip_node")
    private Boolean skipNode;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 节点审批人配置列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<AssigneeConfig> assignees;
    
    /**
     * 节点关联的表单列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<FormConfig> forms;

    /**
     * 节点类型枚举
     */
    public enum NodeType {
        /** 开始节点 */
        START,
        /** 结束节点 */
        END,
        /** 用户任务节点（需要人工处理） */
        USER_TASK,
        /** 服务任务节点（自动执行） */
        SERVICE_TASK,
        /** 脚本任务节点 */
        SCRIPT_TASK,
        /** 发送任务节点 */
        SEND_TASK,
        /** 接收任务节点 */
        RECEIVE_TASK,
        /** 手动任务节点 */
        MANUAL_TASK,
        /** 业务规则任务节点 */
        BUSINESS_RULE_TASK,
        /** 排他网关（条件分支） */
        EXCLUSIVE_GATEWAY,
        /** 并行网关（并行分支） */
        PARALLEL_GATEWAY,
        /** 包容网关 */
        INCLUSIVE_GATEWAY,
        /** 事件网关 */
        EVENT_BASED_GATEWAY,
        /** 调用活动（子流程） */
        CALL_ACTIVITY,
        /** 子流程 */
        SUB_PROCESS
    }
}
