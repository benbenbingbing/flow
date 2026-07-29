package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 某次变更流程冻结的实际目标记录。
 */
@Data
@TableName("entity_change_target_instance")
public class EntityChangeTargetInstance {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String bindingCode;
    private String sourceEntityCode;
    private String sourceRecordId;
    private String processInstanceId;
    private String targetEntityCode;
    private String targetRecordId;
    private Integer baselineVersionNo;
    private String targetDocument;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
