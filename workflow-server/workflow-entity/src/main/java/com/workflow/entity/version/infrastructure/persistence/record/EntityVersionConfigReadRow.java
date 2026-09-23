package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/** 当前配置与所属 active release 的单次 JOIN 投影，仅供持久化读取，不直接对外返回。 */
@Getter
@Setter
public class EntityVersionConfigReadRow extends EntityVersionConfig {
    @TableField(exist = false)
    private String sourceReleaseId;
    @TableField(exist = false)
    private String sourceReleaseDocument;
    @TableField(exist = false)
    private Integer sourceContractVersion;
}
