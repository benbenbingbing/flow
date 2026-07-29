package com.workflow.openapi.connector.config;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("integration_connector_config")
class IntegrationConnectorConfigRecord {

    @TableId
    private String id;
    private String applicationId;
    private String configName;
    private String connectorCode;
    private String status;
    private String configurationDocument;
    private String allowedHostsDocument;
    private Long version;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
