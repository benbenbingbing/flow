package com.workflow.admin.externalsystem.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部系统自定义参数持久化记录。
 */
@Data
@TableName("sys_external_system_parameter")
public class ExternalSystemParameterRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String externalSystemId;
    private String parameterNameZh;
    private String parameterNameEn;
    private String parameterValue;
    private Integer sortOrder;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
