package com.workflow.entity.mutationpolicy.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Entity mutation policy editable draft. */
@Data
@TableName("entity_mutation_policy_config")
public class EntityMutationPolicyConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String entityId;
    private String entityCode;
    private Boolean enabled;
    private String draftDocument;
    private String activeReleaseId;
    private Integer revision;
    private String status;
    private String migrationState;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private Integer deleted;
}
