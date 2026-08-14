package com.workflow.entity.mutationpolicy.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable published entity mutation policy. */
@Data
@TableName("entity_mutation_policy_release")
public class EntityMutationPolicyRelease {

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
