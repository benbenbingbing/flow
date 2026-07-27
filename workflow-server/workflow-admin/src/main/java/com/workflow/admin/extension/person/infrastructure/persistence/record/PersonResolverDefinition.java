package com.workflow.admin.extension.person.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 受控人员解析器目录定义。
 */
@Data
@TableName("process_person_resolver_definition")
public class PersonResolverDefinition {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String resolverCode;
    private String displayName;
    private String description;
    private String beanName;
    private Integer implementationVersion;
    private Integer contractVersion;
    private String supportedUsagesDocument;
    private String extraParamSchemaDocument;
    private Boolean dynamicExtraParams;
    private Boolean enabled;
    private Integer revision;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
