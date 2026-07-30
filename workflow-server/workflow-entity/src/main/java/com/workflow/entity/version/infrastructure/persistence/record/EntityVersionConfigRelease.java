package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运行时不可变的实体版本配置发布快照。
 */
@Data
@TableName("entity_version_config_release")
public class EntityVersionConfigRelease {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String configId;
    private Integer version;
    private String configDocument;
    private String publishedBy;
    private String publishedByName;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
}
