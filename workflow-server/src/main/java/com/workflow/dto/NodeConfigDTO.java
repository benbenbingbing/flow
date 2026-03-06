package com.workflow.dto;

import com.workflow.entity.NodeConfig;
import lombok.Data;

import java.util.List;

/**
 * 节点配置数据传输对象
 * 
 * @description 用于前后端传输节点配置数据的DTO
 *              包含节点基本信息、审批人配置和表单配置
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
public class NodeConfigDTO {

    /**
     * 节点配置ID
     */
    private String id;

    /**
     * 节点ID（BPMN中的唯一标识）
     */
    private String nodeId;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 节点类型
     * START, END, USER_TASK, SERVICE_TASK, EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY
     */
    private NodeConfig.NodeType nodeType;

    /**
     * 审批人配置列表
     */
    private List<AssigneeConfigDTO> assignees;

    /**
     * 表单配置列表
     */
    private List<FormConfigDTO> forms;

    /**
     * 扩展配置JSON
     */
    private String configJson;
}
