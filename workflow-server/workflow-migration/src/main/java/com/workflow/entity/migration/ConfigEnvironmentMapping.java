package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_environment_mapping")
public class ConfigEnvironmentMapping {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String sourceType;
    private String sourceKey;
    private String targetKey;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
