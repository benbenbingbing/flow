package com.workflow.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 人员解析器目录选项。
 */
@Data
public class PersonResolverOptionDTO {

    private String definitionId;
    private String resolverCode;
    private String beanName;
    private String className;
    private String displayName;
    private String description;
    private Integer implementationVersion;
    private Integer contractVersion;
    private Set<String> supportedUsages;
    private Map<String, Object> extraParamSchema;
    private Boolean dynamicExtraParams;
    private Boolean configured;
    private Boolean available;
    private Boolean enabled;
    private Integer revision;
}
