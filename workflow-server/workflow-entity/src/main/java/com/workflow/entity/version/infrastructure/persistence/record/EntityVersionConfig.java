package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 每个实体唯一的一份当前数据版本配置。 */
@Data
@TableName("entity_version_config")
public class EntityVersionConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String entityId;
    private String entityCode;
    private Boolean enabled;
    private String configDocument;
    private Integer revision;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private Integer deleted;
}
