package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务定义 - 服务编排
 */
@Data
@TableName("service_definition")
public class ServiceDefinition {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String serviceCode;
    private String serviceName;
    
    /**
     * 服务类型：ORCHESTRATION编排/SCRIPT脚本/PROXY代理
     */
    private String serviceType;
    
    private String categoryId;
    private String description;
    private String inputParams;
    private String outputParams;
    private String flowConfig;
    private String variables;
    private Integer timeoutMs;
    private String retryConfig;
    private String exceptionHandler;
    
    private Integer version;
    private String status;
    
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 非持久化字段
    private transient String categoryName;
}
