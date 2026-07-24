package com.workflow.dto.permission;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表数据范围绑定 DTO。
 * 描述某个列表与数据范围策略的绑定关系，含匹配条件与生效时段。
 */
@Data
public class EntityListScopeBindingDTO {
    /** 绑定 ID */
    private String id;
    /** 实体编码 */
    private String entityCode;
    /** 关联的数据范围策略 ID */
    private String policyId;
    /** 列表标识 */
    private String listKey;
    /** 适用对象匹配条件 */
    private MatchConfigDTO matchConfig;
    /** 规则效果（如 INCLUDE/EXCLUDE） */
    private String ruleEffect;
    /** 是否启用（0-禁用，1-启用） */
    private Integer enabled;
    /** 生效起始时间 */
    private LocalDateTime effectiveStartTime;
    /** 生效结束时间 */
    private LocalDateTime effectiveEndTime;
}
