package com.workflow.dto.permission;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntityListScopeBindingDTO {
    private String id;
    private String entityCode;
    private String policyId;
    private String listKey;
    private MatchConfigDTO matchConfig;
    private String ruleEffect;
    private Integer enabled;
    private LocalDateTime effectiveStartTime;
    private LocalDateTime effectiveEndTime;
}
