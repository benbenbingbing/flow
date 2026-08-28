package com.workflow.admin.identity.position.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局职务定义；职务本身不携带组织范围，也不产生系统权限。
 */
@Data
@TableName("sys_position")
public class SysPosition {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String positionCode;
    private String positionName;
    private String applicableUnitType;
    private String holderMode;
    private Boolean builtIn;
    private String status;
    private Integer sortOrder;
    private String description;
    private Integer revision;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public enum ApplicableUnitType {
        ORG,
        DEPT,
        ANY
    }

    public enum HolderMode {
        SINGLE,
        MULTIPLE
    }

    public enum Status {
        ENABLED,
        DISABLED
    }
}
