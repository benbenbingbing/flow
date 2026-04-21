package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务节点定义
 */
@Data
@TableName("service_node")
public class ServiceNode {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String serviceId;
    private String nodeId;
    
    /**
     * 节点类型：START/END/ENTITY_CRUD/HTTP/SQL/SCRIPT/CONDITION/PARALLEL/JOIN/LOOP/SUBFLOW/PROCESS/MESSAGE/DELAY/MAPPING/LOG
     */
    private String nodeType;
    
    private String nodeName;
    private BigDecimal positionX;
    private BigDecimal positionY;
    private String config;
    private String inputMapping;
    private String outputMapping;
    private String nextNodes;
    private String conditionExpression;
    
    private LocalDateTime createdAt;
}
