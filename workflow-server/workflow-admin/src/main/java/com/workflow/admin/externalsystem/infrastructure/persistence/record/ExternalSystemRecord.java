package com.workflow.admin.externalsystem.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部系统基本信息持久化记录。
 */
@Data
@TableName("sys_external_system")
public class ExternalSystemRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String systemName;
    private String systemCode;
    private String status;
    private String address;
    private String description;
    private Long version;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
