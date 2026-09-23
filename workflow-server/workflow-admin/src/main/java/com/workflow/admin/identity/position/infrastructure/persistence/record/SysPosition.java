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

    /**
     * 定义适用单元类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum ApplicableUnitType {
        ORG,
        DEPT,
        ANY
    }

    /**
     * 定义持有者模式的可选值；调用方据此选择对应的处理分支。
     */
    public enum HolderMode {
        SINGLE,
        MULTIPLE
    }

    /**
     * 定义状态的可选值；调用方据此选择对应的处理分支。
     */
    public enum Status {
        ENABLED,
        DISABLED
    }
}
