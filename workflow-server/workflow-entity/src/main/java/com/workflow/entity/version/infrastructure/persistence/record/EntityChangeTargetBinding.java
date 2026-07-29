package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 变更申请到原始实体记录的目标绑定。
 */
@Data
@TableName("entity_change_target_binding")
public class EntityChangeTargetBinding {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String configId;
    private String bindingCode;
    private String bindingName;
    private String sourceEntityCode;
    private String targetEntityCode;
    private String resolverType;
    private String resolverCode;
    private String resolverConfigDocument;
    private String mappingDocument;
    private String applyStrategy;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
